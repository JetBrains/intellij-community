/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.util.io;

import com.intellij.openapi.Forceable;
import com.intellij.openapi.diagnostic.Logger;

import java.io.*;
import java.nio.ByteBuffer;

public class RandomAccessDataFile implements Forceable {
  protected static final Logger LOG = Logger.getInstance("#com.intellij.util.io.RandomAccessDataFile");

  private final static OpenChannelsCache ourCache = new OpenChannelsCache(150, "rw");
  private static int ourFilesCount = 0;

  private final int myCount = ourFilesCount++;
  private final File myFile;
  private final PagePool myPool;
  private long lastSeek = -1l;

  private final byte[] myTypedIOBuffer = new byte[8];

  private final FileWriter log;

  private volatile long mySize;
  private volatile boolean myIsDirty = false;
  private volatile boolean myIsDisposed = false;

  private static final boolean DEBUG = false;

  public RandomAccessDataFile(final File file) throws IOException {
    this(file, PagePool.SHARED);
  }

  public RandomAccessDataFile(final File file, final PagePool pool) throws IOException {
    myPool = pool;
    myFile = file;
    if (!file.exists()) {
      throw new FileNotFoundException(file.getPath() + " does not exist");
    }

    mySize = file.length();
    if (DEBUG) {
      log = new FileWriter(file.getPath() + ".log");
    }
    else {
      log = null;
    }
  }

  public void put(long addr, byte[] bytes, int off, int len) {
    assertNotDisposed();

    myIsDirty = true;
    mySize = Math.max(mySize, addr + len);

    while (len > 0) {
      final Page page = myPool.alloc(this, addr);
      int written = page.put(addr, bytes, off, len);
      len -= written;
      addr += written;
      off += written;
    }
  }

  public void get(long addr, byte[] bytes, int off, int len) {
    assertNotDisposed();

    while (len > 0) {
      final Page page = myPool.alloc(this, addr);
      int read = page.get(addr, bytes, off, len);
      len -= read;
      addr += read;
      off += read;
    }
  }

  private void releaseFile() {
    ourCache.releaseChannel(myFile);
  }

  private RandomAccessFile getFile() throws FileNotFoundException {
    return ourCache.getChannel(myFile);
  }

  public void putInt(long addr, int value) {
    Bits.putInt(myTypedIOBuffer, 0, value);
    put(addr, myTypedIOBuffer, 0, 4);
  }

  public int getInt(long addr) {
    get(addr, myTypedIOBuffer, 0, 4);
    return Bits.getInt(myTypedIOBuffer, 0);
  }

  public void putLong(long addr, long value) {
    Bits.putLong(myTypedIOBuffer, 0, value);
    put(addr, myTypedIOBuffer, 0, 8);
  }

  public void putByte(final long addr, final byte b) {
    myTypedIOBuffer[0] = b;
    put(addr, myTypedIOBuffer, 0, 1);
  }

  public byte getByte(long addr) {
    get(addr, myTypedIOBuffer, 0, 1);
    return myTypedIOBuffer[0];
  }

  public long getLong(long addr) {
    get(addr, myTypedIOBuffer, 0, 8);
    return Bits.getLong(myTypedIOBuffer, 0);
  }

  public String getUTF(long addr) {
    try {
      int len = getInt(addr);
      byte[] bytes = new byte[ len ];
      get(addr + 4, bytes, 0, len);
      return new String(bytes, "UTF-8");
    }
    catch (UnsupportedEncodingException e) {
      // Can't be
      return "";
    }
  }

  public void putUTF(long addr, String value) {
    try {
      final byte[] bytes = value.getBytes("UTF-8");
      putInt(addr, bytes.length);
      put(addr + 4, bytes, 0, bytes.length);
    }
    catch (UnsupportedEncodingException e) {
      // Can't be
    }
  }

  public long length() {
    assertNotDisposed();
    return mySize;
  }

  public long physicalLength() {
    assertNotDisposed();

    long res;

    try {
      res = getFile().length();
    }
    catch (IOException e) {
      return 0;
    }

    releaseFile();
    return res;
  }

  public void dispose() {
    if (myIsDisposed) return;
    myPool.flushPages(this);
    ourCache.closeChannel(myFile);
    
    myIsDisposed = true;
  }

  public void force() {
    assertNotDisposed();
    if (isDirty()) {
      myPool.flushPages(this);
      myIsDirty = false;
    }
  }

  public void flushSomePages(int maxPagesToFlush) {
    assertNotDisposed();
    if (isDirty()) {
      myIsDirty = !myPool.flushPages(this, maxPagesToFlush);
    }
  }

  public boolean isDirty() {
    assertNotDisposed();
    return myIsDirty;
  }

  public boolean isDisposed() {
    return myIsDisposed;
  }

  private void assertNotDisposed() {
    LOG.assertTrue(!myIsDisposed, "storage file is disposed: " + myFile);
  }

  public static int totalReads = 0;
  public static long totalReadBytes = 0;

  public static int seekcount = 0;
  public static int totalWrites = 0;
  public static long totalWriteBytes = 0;

  void loadPage(final Page page) {
    try {
      final RandomAccessFile file = getFile();
      try {
        synchronized (file) {
          seek(file, page.getOffset());
          final ByteBuffer buf = page.getBuf();

          totalReads++;
          totalReadBytes += Page.PAGE_SIZE;

          if (DEBUG) {
            log.write("Read at: \t" + page.getOffset() + "\t len: " + Page.PAGE_SIZE + ", size: " + mySize + "\n");
          }
          file.read(buf.array(), 0, Page.PAGE_SIZE);
          lastSeek += Page.PAGE_SIZE;
        }
      }
      finally {
        releaseFile();
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  void flushPage(final Page page, int start, int end) {
    try {
      flush(page.getBuf(), page.getOffset() + start, start, end - start);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void flush(final ByteBuffer buf, final long fileOffset, final int bufOffset, int length) throws IOException {
    if (fileOffset + length > mySize) {
      length = (int)(mySize - fileOffset);
    }

    final RandomAccessFile file = getFile();
    try {
      synchronized (file) {
        seek(file, fileOffset);

        totalWrites++;
        totalWriteBytes += length;

        if (DEBUG) {
          log.write("Write at: \t" + fileOffset + "\t len: " + length + ", size: " + mySize + ", filesize: " + file.length() + "\n");
        }
        file.write(buf.array(), bufOffset, length);
        lastSeek += length;
      }
    }
    finally {
      releaseFile();
    }
  }

  private void seek(final RandomAccessFile file, final long fileOffset) throws IOException {
    if (DEBUG) {
      if (lastSeek != -1L && fileOffset != lastSeek) {
        long delta = fileOffset - lastSeek;
        seekcount++;
        log.write("Seeking: " + delta + "\n");
      }
      lastSeek = fileOffset;
    }

    file.seek(fileOffset);
  }

  public int hashCode() {
    return myCount;
  }

  @Override
  public synchronized String toString() {
    return "RandomAccessFile[" + myFile + ", dirty=" + myIsDirty + "]";
  }
}
