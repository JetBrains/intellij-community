/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.io.tar;

import org.jetbrains.annotations.NotNull;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * The TarInputStream reads a UNIX tar archive as an InputStream.
 * methods are provided to position at each successive entry in
 * the archive, and the read each entry as a normal input stream
 * using read().
 *
 */
// Copy-pasted from org.apache.tools.tar.TarInputStream
public class TarInputStream extends FilterInputStream {
  private static final int SMALL_BUFFER_SIZE = 256;
  private static final int BUFFER_SIZE = 8 * 1024;
  private static final int LARGE_BUFFER_SIZE = 32 * 1024;
  private static final int BYTE_MASK = 0xFF;

  // CheckStyle:VisibilityModifier OFF - bc
  protected boolean debug;
  protected boolean hasHitEOF;
  protected long entrySize;
  protected long entryOffset;
  protected byte[] readBuf;
  protected TarBuffer buffer;
  protected TarEntry currEntry;

  /**
   * This contents of this array is not used at all in this class,
   * it is only here to avoid repreated object creation during calls
   * to the no-arg read method.
   */
  protected byte[] oneBuf;

  // CheckStyle:VisibilityModifier ON

  /**
   * Constructor for TarInputStream.
   * @param is the input stream to use
   */
  public TarInputStream(InputStream is) {
    this(is, TarBuffer.DEFAULT_BLKSIZE, TarBuffer.DEFAULT_RCDSIZE);
  }

  /**
   * Constructor for TarInputStream.
   * @param is the input stream to use
   * @param blockSize the block size to use
   */
  public TarInputStream(InputStream is, int blockSize) {
    this(is, blockSize, TarBuffer.DEFAULT_RCDSIZE);
  }

  /**
   * Constructor for TarInputStream.
   * @param is the input stream to use
   * @param blockSize the block size to use
   * @param recordSize the record size to use
   */
  public TarInputStream(InputStream is, int blockSize, int recordSize) {
    super(is);

    this.buffer = new TarBuffer(is, blockSize, recordSize);
    this.readBuf = null;
    this.oneBuf = new byte[1];
    this.debug = false;
    this.hasHitEOF = false;
  }

  /**
   * Sets the debugging flag.
   *
   * @param debug True to turn on debugging.
   */
  public void setDebug(boolean debug) {
    this.debug = debug;
    buffer.setDebug(debug);
  }

  /**
   * Closes this stream. Calls the TarBuffer's close() method.
   * @throws java.io.IOException on error
   */
  @Override
  public void close() throws IOException {
    buffer.close();
  }

  /**
   * Get the record size being used by this stream's TarBuffer.
   *
   * @return The TarBuffer record size.
   */
  public int getRecordSize() {
    return buffer.getRecordSize();
  }

  /**
   * Get the available data that can be read from the current
   * entry in the archive. This does not indicate how much data
   * is left in the entire archive, only in the current entry.
   * This value is determined from the entry's size header field
   * and the amount of data already read from the current entry.
   * Integer.MAX_VALUE is returen in case more than Integer.MAX_VALUE
   * bytes are left in the current entry in the archive.
   *
   * @return The number of available bytes for the current entry.
   * @throws IOException for signature
   */
  @Override
  public int available() throws IOException {
    if (entrySize - entryOffset > Integer.MAX_VALUE) {
      return Integer.MAX_VALUE;
    }
    return (int) (entrySize - entryOffset);
  }

  /**
   * Skip bytes in the input buffer. This skips bytes in the
   * current entry's data, not the entire archive, and will
   * stop at the end of the current entry's data if the number
   * to skip extends beyond that point.
   *
   * @param numToSkip The number of bytes to skip.
   * @return the number actually skipped
   * @throws IOException on error
   */
  @Override
  public long skip(long numToSkip) throws IOException {
    // REVIEW
    // This is horribly inefficient, but it ensures that we
    // properly skip over bytes via the TarBuffer...
    //
    byte[] skipBuf = new byte[BUFFER_SIZE];
    long skip = numToSkip;
    while (skip > 0) {
      int realSkip = (int) (skip > skipBuf.length ? skipBuf.length : skip);
      int numRead = read(skipBuf, 0, realSkip);
      if (numRead == -1) {
        break;
      }
      skip -= numRead;
    }
    return (numToSkip - skip);
  }

  /**
   * Since we do not support marking just yet, we return false.
   *
   * @return False.
   */
  @Override
  public boolean markSupported() {
    return false;
  }

  /**
   * Since we do not support marking just yet, we do nothing.
   *
   * @param markLimit The limit to mark.
   */
  @Override
  public void mark(int markLimit) {
  }

  /**
   * Since we do not support marking just yet, we do nothing.
   */
  @Override
  public void reset() {
  }

  /**
   * Get the next entry in this tar archive. This will skip
   * over any remaining data in the current entry, if there
   * is one, and place the input stream at the header of the
   * next entry, and read the header and instantiate a new
   * TarEntry from the header bytes and return that entry.
   * If there are no more entries in the archive, null will
   * be returned to indicate that the end of the archive has
   * been reached.
   *
   * @return The next TarEntry in the archive, or null.
   * @throws IOException on error
   */
  public TarEntry getNextEntry() throws IOException {
    if (hasHitEOF) {
      return null;
    }

    if (currEntry != null) {
      long numToSkip = entrySize - entryOffset;

      if (debug) {
        System.err.println("TarInputStream: SKIP currENTRY '"
                           + currEntry.getName() + "' SZ "
                           + entrySize + " OFF "
                           + entryOffset + "  skipping "
                           + numToSkip + " bytes");
      }

      while (numToSkip > 0) {
        long skipped = skip(numToSkip);
        if (skipped <= 0) {
          throw new RuntimeException("failed to skip current tar"
                                     + " entry");
        }
        numToSkip -= skipped;
      }

      readBuf = null;
    }

    byte[] headerBuf = buffer.readRecord();

    if (headerBuf == null) {
      if (debug) {
        System.err.println("READ NULL RECORD");
      }
      hasHitEOF = true;
    } else if (buffer.isEOFRecord(headerBuf)) {
      if (debug) {
        System.err.println("READ EOF RECORD");
      }
      hasHitEOF = true;
    }

    if (hasHitEOF) {
      currEntry = null;
    } else {
      currEntry = new TarEntry(headerBuf);

      if (debug) {
        System.err.println("TarInputStream: SET CURRENTRY '"
                           + currEntry.getName()
                           + "' size = "
                           + currEntry.getSize());
      }

      entryOffset = 0;

      entrySize = currEntry.getSize();
    }

    if (currEntry != null && currEntry.isGNULongNameEntry()) {
      // read in the name
      StringBuffer longName = new StringBuffer();
      byte[] buf = new byte[SMALL_BUFFER_SIZE];
      int length = 0;
      while ((length = read(buf)) >= 0) {
        longName.append(new String(buf, 0, length));
      }
      getNextEntry();
      if (currEntry == null) {
        // Bugzilla: 40334
        // Malformed tar file - long entry name not followed by entry
        return null;
      }
      // remove trailing null terminator
      if (longName.length() > 0
          && longName.charAt(longName.length() - 1) == 0) {
        longName.deleteCharAt(longName.length() - 1);
      }
      currEntry.setName(longName.toString());
    }

    return currEntry;
  }

  /**
   * Reads a byte from the current tar archive entry.
   *
   * This method simply calls read( byte[], int, int ).
   *
   * @return The byte read, or -1 at EOF.
   * @throws IOException on error
   */
  @Override
  public int read() throws IOException {
    int num = read(oneBuf, 0, 1);
    return num == -1 ? -1 : ((int) oneBuf[0]) & BYTE_MASK;
  }

  /**
   * Reads bytes from the current tar archive entry.
   *
   * This method is aware of the boundaries of the current
   * entry in the archive and will deal with them as if they
   * were this stream's start and EOF.
   *
   * @param buf The buffer into which to place bytes read.
   * @param offset The offset at which to place bytes read.
   * @param numToRead The number of bytes to read.
   * @return The number of bytes read, or -1 at EOF.
   * @throws IOException on error
   */
  @Override
  public int read(@NotNull byte[] buf, int offset, int numToRead) throws IOException {
    int totalRead = 0;

    if (entryOffset >= entrySize) {
      return -1;
    }

    if ((numToRead + entryOffset) > entrySize) {
      numToRead = (int) (entrySize - entryOffset);
    }

    if (readBuf != null) {
      int sz = (numToRead > readBuf.length) ? readBuf.length
                                            : numToRead;

      System.arraycopy(readBuf, 0, buf, offset, sz);

      if (sz >= readBuf.length) {
        readBuf = null;
      } else {
        int newLen = readBuf.length - sz;
        byte[] newBuf = new byte[newLen];

        System.arraycopy(readBuf, sz, newBuf, 0, newLen);

        readBuf = newBuf;
      }

      totalRead += sz;
      numToRead -= sz;
      offset += sz;
    }

    while (numToRead > 0) {
      byte[] rec = buffer.readRecord();

      if (rec == null) {
        // Unexpected EOF!
        throw new IOException("unexpected EOF with " + numToRead
                              + " bytes unread");
      }

      int sz = numToRead;
      int recLen = rec.length;

      if (recLen > sz) {
        System.arraycopy(rec, 0, buf, offset, sz);

        readBuf = new byte[recLen - sz];

        System.arraycopy(rec, sz, readBuf, 0, recLen - sz);
      } else {
        sz = recLen;

        System.arraycopy(rec, 0, buf, offset, recLen);
      }

      totalRead += sz;
      numToRead -= sz;
      offset += sz;
    }

    entryOffset += totalRead;

    return totalRead;
  }

  /**
   * Copies the contents of the current tar archive entry directly into
   * an output stream.
   *
   * @param out The OutputStream into which to write the entry's data.
   * @throws IOException on error
   */
  public void copyEntryContents(OutputStream out) throws IOException {
    byte[] buf = new byte[LARGE_BUFFER_SIZE];

    while (true) {
      int numRead = read(buf, 0, buf.length);

      if (numRead == -1) {
        break;
      }

      out.write(buf, 0, numRead);
    }
  }
}
