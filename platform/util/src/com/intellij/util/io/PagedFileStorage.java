// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.openapi.Forceable;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class PagedFileStorage implements Forceable {
  private static final Logger LOG = Logger.getInstance(PagedFileStorage.class);

  public static final int MB = 1024 * 1024;
  public static final int BUFFER_SIZE = StorageLock.BUFFER_SIZE;

  private static final ByteOrder ourNativeByteOrder = ByteOrder.nativeOrder();
  private final static ThreadLocal<byte[]> ourTypedIOBuffer = ThreadLocal.withInitial(() -> new byte[8]);

  // It is important to have ourLock after previous static constants as it depends on them
  static final StorageLock ourLock = new StorageLock();

  public static final ThreadLocal<StorageLockContext> THREAD_LOCAL_STORAGE_LOCK_CONTEXT = new ThreadLocal<>();

  private final StorageLockContext myStorageLockContext;
  private final boolean myNativeBytesOrder;
  private int myStorageIndex; // -1 when closed
  private final PagedFileStorageCache myLastAccessedBufferCache = new PagedFileStorageCache();

  private final Path myFile;
  private final boolean myReadOnly;
  protected final int myPageSize;
  protected final boolean myValuesAreBufferAligned;

  private volatile boolean isDirty;
  private volatile long mySize = -1;

  public PagedFileStorage(Path file,
                          @Nullable StorageLockContext storageLockContext,
                          int pageSize,
                          boolean valuesAreBufferAligned,
                          boolean nativeBytesOrder) {
    myFile = file;
    // TODO read-only flag should be extracted from PersistentHashMapValueStorage.CreationTimeOptions
    myReadOnly = PersistentHashMapValueStorage.CreationTimeOptions.READONLY.get() == Boolean.TRUE;

    StorageLockContext context = THREAD_LOCAL_STORAGE_LOCK_CONTEXT.get();
    if (context != null) {
      if (storageLockContext != null && storageLockContext != context) {
        throw new IllegalStateException();
      }
      storageLockContext = context;
    }

    myStorageLockContext = storageLockContext != null ? storageLockContext : ourLock.myDefaultContext;
    myPageSize = Math.max(pageSize > 0 ? pageSize : BUFFER_SIZE, Page.PAGE_SIZE);
    myValuesAreBufferAligned = valuesAreBufferAligned;
    myStorageIndex = myStorageLockContext.getStorageLock().registerPagedFileStorage(this);
    myNativeBytesOrder = nativeBytesOrder;
  }

  public int getPageSize() {
    return myPageSize;
  }

  public void lockRead() {
    myStorageLockContext.lockRead();
  }

  public void unlockRead() {
    myStorageLockContext.unlockRead();
  }

  public void lockWrite() {
    myStorageLockContext.lockWrite();
  }

  public void unlockWrite() {
    myStorageLockContext.unlockWrite();
  }

  public StorageLockContext getStorageLockContext() {
    return myStorageLockContext;
  }

  public Path getFile() {
    return myFile;
  }

  public void putInt(long addr, int value) {
    if (myValuesAreBufferAligned) {
      long page = addr / myPageSize;
      int page_offset = (int)(addr % myPageSize);
      getBuffer(page).putInt(page_offset, value);
    } else {
      Bits.putInt(getThreadLocalTypedIOBuffer(), 0, value);
      put(addr, getThreadLocalTypedIOBuffer(), 0, 4);
    }
  }

  public int getInt(long addr) {
    if (myValuesAreBufferAligned) {
      long page = addr / myPageSize;
      int page_offset = (int) (addr % myPageSize);
      return getReadOnlyBuffer(page).getInt(page_offset);
    } else {
      get(addr, getThreadLocalTypedIOBuffer(), 0, 4);
      return Bits.getInt(getThreadLocalTypedIOBuffer(), 0);
    }
  }

  public int getOffsetInPage(long addr) {
    return (int)(addr % myPageSize);
  }

  public ByteBufferWrapper getByteBuffer(long address, boolean modify) {
    long page = address / myPageSize;
    assert page >= 0 && page <= StorageLock.MAX_PAGES_COUNT:address + " in " + myFile;
    return getBufferWrapper(page, modify, myReadOnly);
  }

  public void putLong(long addr, long value) {
    if (myValuesAreBufferAligned) {
      long page = addr / myPageSize;
      int page_offset = (int)(addr % myPageSize);
      getBuffer(page).putLong(page_offset, value);
    } else {
      Bits.putLong(getThreadLocalTypedIOBuffer(), 0, value);
      put(addr, getThreadLocalTypedIOBuffer(), 0, 8);
    }
  }

  public long getLong(long addr) {
    if (myValuesAreBufferAligned) {
      long page = addr / myPageSize;
      int page_offset = (int)(addr % myPageSize);
      return getReadOnlyBuffer(page).getLong(page_offset);
    } else {
      get(addr, getThreadLocalTypedIOBuffer(), 0, 8);
      return Bits.getLong(getThreadLocalTypedIOBuffer(), 0);
    }
  }

  public byte get(long index) {
    long page = index / myPageSize;
    int offset = (int)(index % myPageSize);

    return getReadOnlyBuffer(page).get(offset);
  }

  public void put(long index, byte value) {
    long page = index / myPageSize;
    int offset = (int)(index % myPageSize);

    getBuffer(page).put(offset, value);
  }

  public void get(long index, byte[] dst, int offset, int length) {
    long i = index;
    int o = offset;
    int l = length;

    while (l > 0) {
      long page = i / myPageSize;
      int page_offset = (int) (i % myPageSize);

      int page_len = Math.min(l, myPageSize - page_offset);
      final ByteBuffer buffer = getReadOnlyBuffer(page);
      // TODO do a proper synchronization
      //noinspection SynchronizationOnLocalVariableOrMethodParameter
      synchronized (buffer) {
        try {
          buffer.position(page_offset);
        }
        catch (IllegalArgumentException iae) {
          throw new IllegalArgumentException("can't position buffer to offset " + page_offset + ", " +
                                             "buffer.limit=" + buffer.limit() + ", " +
                                             "page=" + page + ", " +
                                             "file=" + myFile.getFileName() + ", "+
                                             "file.length=" + length());
        }
        buffer.get(dst, o, page_len);
      }

      l -= page_len;
      o += page_len;
      i += page_len;
    }
  }

  public void put(long index, byte[] src, int offset, int length) {
    long i = index;
    int o = offset;
    int l = length;

    while (l > 0) {
      long page = i / myPageSize;
      int page_offset = (int) (i % myPageSize);

      int page_len = Math.min(l, myPageSize - page_offset);
      final ByteBuffer buffer = getBuffer(page);
      // TODO do a proper synchronization
      //noinspection SynchronizationOnLocalVariableOrMethodParameter
      synchronized (buffer) {
        try {
          buffer.position(page_offset);
        }
        catch (IllegalArgumentException iae) {
          throw new IllegalArgumentException("can't position buffer to offset " + page_offset);
        }
        buffer.put(src, o, page_len);
      }
      l -= page_len;
      o += page_len;
      i += page_len;
    }
  }

  public void close() {
    try {
      force();
    }
    finally {
      unmapAll();
      myStorageLockContext.getStorageLock().removeStorage(myStorageIndex);
      myStorageIndex = -1;
    }
  }

  private void unmapAll() {
    myStorageLockContext.getStorageLock().unmapBuffersForOwner(myStorageIndex, myStorageLockContext, false);
    myLastAccessedBufferCache.clear();
  }

  public void resize(long newSize) throws IOException {
    long oldSize = Files.exists(myFile) ? Files.size(myFile) : 0;
    if (oldSize == newSize && oldSize == length()) return;

    final long started = IOStatistics.DEBUG ? System.currentTimeMillis():0;
    myStorageLockContext.getStorageLock().invalidateBuffer(myStorageIndex | (int)(oldSize / myPageSize)); // TODO long page
    final long unmapAllFinished = IOStatistics.DEBUG ? System.currentTimeMillis():0;

    resizeFile(newSize);

    // it is not guaranteed that new partition will consist of null
    // after resize, so we should fill it manually
    long delta = newSize - oldSize;
    if (delta > 0) fillWithZeros(oldSize, delta);

    if (IOStatistics.DEBUG) {
      long finished = System.currentTimeMillis();
      if (finished - started > IOStatistics.MIN_IO_TIME_TO_REPORT) {
        IOStatistics.dump("Resized "+myFile + " from " + oldSize + " to " + newSize + " for " + (finished - started) + ", unmap all:" + (finished - unmapAllFinished));
      }
    }
  }

  private void resizeFile(long newSize) throws IOException {
    mySize = -1;
    try (RandomAccessFile raf = new RandomAccessFile(myFile.toFile(), "rw")) {
      raf.setLength(newSize);
    }
    mySize = newSize;
  }

  private static final int MAX_FILLER_SIZE = 8192;

  private void fillWithZeros(long from, long length) {
    byte[] buff = new byte[MAX_FILLER_SIZE];
    Arrays.fill(buff, (byte)0);

    while (length > 0) {
      final int filled = Math.min((int)length, MAX_FILLER_SIZE);
      put(from, buff, 0, filled);
      length -= filled;
      from += filled;
    }
  }

  public final long length() {
    long size = mySize;
    if (size == -1) {
      if (Files.exists(myFile)) {
        try {
          mySize = size = Files.size(myFile);
        }
        catch (IOException e) {
          LOG.error(e);
        }
      } else {
        mySize = size = 0;
      }
    }
    return size;
  }

  private ByteBuffer getBuffer(long page) {
    return getBufferWrapper(page, true, myReadOnly).getCachedBuffer();
  }

  private ByteBuffer getReadOnlyBuffer(long page) {
    return getBufferWrapper(page, false, myReadOnly).getCachedBuffer();
  }

  private ByteBufferWrapper getBufferWrapper(long page, boolean modify, boolean readOnly) {
    ByteBufferWrapper pageFromCache =
      myLastAccessedBufferCache.getPageFromCache(page, myStorageLockContext.getStorageLock().getMappingChangeCount(), readOnly);
    if (pageFromCache != null) {
      if (modify) markDirty(pageFromCache);
      return pageFromCache;
    }

    try {
      assert page >= 0 && page <= StorageLock.MAX_PAGES_COUNT:page;

      if (myStorageIndex == -1) {
        throw new IOException("storage is already closed; path " + myFile);
      }
      ByteBufferWrapper byteBufferWrapper = myStorageLockContext.getStorageLock().get(myStorageIndex | (int)page, !modify, readOnly); // TODO: long page
      if (modify) markDirty(byteBufferWrapper);
      ByteBuffer buf = byteBufferWrapper.getBuffer();
      if (myNativeBytesOrder && buf.order() != ourNativeByteOrder) {
        buf.order(ourNativeByteOrder);
      }

      myLastAccessedBufferCache.updateCache(page, byteBufferWrapper, myStorageLockContext.getStorageLock().getMappingChangeCount());

      return byteBufferWrapper;
    }
    catch (IOException e) {
      throw new MappingFailedException("Cannot map buffer", e);
    }
  }

  private void markDirty(ByteBufferWrapper buffer) {
    if (!isDirty) isDirty = true;
    buffer.markDirty();
  }

  private static byte[] getThreadLocalTypedIOBuffer() {
    return ourTypedIOBuffer.get();
  }

  @Override
  public void force() {
    long started = IOStatistics.DEBUG ? System.currentTimeMillis():0;
    if (isDirty) {
      myStorageLockContext.getStorageLock().flushBuffersForOwner(myStorageIndex, myStorageLockContext);
      isDirty = false;
    }

    if (IOStatistics.DEBUG) {
      long finished = System.currentTimeMillis();
      if (finished - started > IOStatistics.MIN_IO_TIME_TO_REPORT) {
        IOStatistics.dump("Flushed "+myFile + " for " + (finished - started));
      }
    }
  }

  @Override
  public boolean isDirty() {
    return isDirty;
  }
}
