/* Copyright (c) 2001-2011, The HSQL Development Group
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * Neither the name of the HSQL Development Group nor the names of its
 * contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL HSQL DEVELOPMENT GROUP, HSQLDB.ORG,
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */


package org.hsqldb.persist;

import org.hsqldb.Database;
import org.hsqldb.RowAVLDisk;
import org.hsqldb.error.Error;
import org.hsqldb.error.ErrorCode;
import org.hsqldb.lib.FileUtil;
import org.hsqldb.lib.Iterator;

/**
 * A file-based row store for temporary CACHED table persistence.<p>
 *
 * @author Fred Toussi (fredt@users dot sourceforge.net)
 * @version 2.2.9
 * @since 1.9.0
 */
public class DataFileCacheSession extends DataFileCache {

    public DataFileCacheSession(Database db, String baseFileName) {
        super(db, baseFileName);
    }

    /**
     * Initial external parameters are set here. The size if fixed.
     */
    protected void initParams(Database database, String baseFileName) {

        this.dataFileName = baseFileName + ".data.tmp";
        this.database     = database;
        fa                = FileUtil.getFileUtil();
        dataFileScale     = 64;
        cachedRowPadding  = dataFileScale;
        initialFreePos    = dataFileScale;
        maxCacheRows      = 2048;
        maxCacheBytes     = maxCacheRows * 1024;
        maxDataFileSize   = (long) Integer.MAX_VALUE * dataFileScale;
        dataFile          = null;
    }

    /**
     * Opens the *.data file for this cache.
     */
    public void open(boolean readonly) {

        try {
            dataFile = new ScaledRAFile(database, dataFileName, false, false);
            fileFreePosition = initialFreePos;

            initBuffers();

            freeBlocks = new DataFileBlockManager(0, dataFileScale, 0, 0);
        } catch (Throwable t) {
            database.logger.logWarningEvent("Failed to open Session RA file",
                                            t);
            close(false);

            throw Error.error(t, ErrorCode.FILE_IO_ERROR,
                              ErrorCode.M_DataFileCache_open, new Object[] {
                t.toString(), dataFileName
            });
        }
    }

    /**
     *  Parameter write is always false. The backing file is simply closed and
     *  deleted.
     */
    public void close(boolean write) {

        writeLock.lock();

        try {
            cache.clear();

            if (dataFile != null) {
                dataFile.close();

                dataFile = null;

                fa.removeElement(dataFileName);
            }
        } catch (Throwable t) {
            database.logger.logWarningEvent("Failed to close Session RA file",
                                            t);

            throw Error.error(t, ErrorCode.FILE_IO_ERROR,
                              ErrorCode.M_DataFileCache_close, new Object[] {
                t.toString(), dataFileName
            });
        } finally {
            writeLock.unlock();
        }
    }

    public void clear() {

        Iterator it = cache.getIterator();

        while(it.hasNext()) {
            RowAVLDisk row = (RowAVLDisk) it.next();
            row.setInMemory(false);
            row.destroy();
        }
        super.clear();
    }

}
