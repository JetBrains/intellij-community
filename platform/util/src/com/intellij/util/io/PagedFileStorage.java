// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.openapi.Forceable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ThrowableNotNullFunction;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.io.FileChannelInterruptsRetryer.FileChannelIdempotentOperation;
import com.intellij.util.io.OpenChannelsCache.FileChannelOperation;
import com.intellij.util.io.storage.AbstractStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import static com.intellij.util.io.PageCacheUtils.CHANNELS_CACHE;

public final class PagedFileStorage implements Forceable/*, PagedStorage*/, Closeable {
  static final Logger LOG = Logger.getInstance(PagedFileStorage.class);

  private static final int DEFAULT_PAGE_SIZE = PageCacheUtils.DEFAULT_PAGE_SIZE;

  private static final @NotNull ThreadLocal<byte[]> ourTypedIOBuffer = ThreadLocal.withInitial(() -> new byte[8]);

  public static final @NotNull ThreadLocal<StorageLockContext> THREAD_LOCAL_STORAGE_LOCK_CONTEXT = new ThreadLocal<>();

  private final @NotNull StorageLockContext myStorageLockContext;
  private final boolean myNativeBytesOrder;
  /**
   * Storage id(key), as returned by {@link FilePageCache#registerPagedFileStorage(PagedFileStorage)}, or -1 when closed
   */
  private long myStorageIndex;
  /**
   * Small (3 pages max) 'local' (per-storage) pages cache. Faster (probably) than {@link FilePageCache},
   * but the main idea is that buffers in that cache are 'locked', i.e. can't be reclaimed by {@link FilePageCache},
   * hence this is also a way to reduce 'page faults' on most recent buffers
   */
  private final @NotNull PagedFileStorageCache myLastAccessedBufferCache = new PagedFileStorageCache();

  private final @NotNull Path myFile;
  private final boolean myReadOnly;
  private final Object myInputStreamLock = new Object();

  private final int myPageSize;
  private final boolean myValuesAreBufferAligned;

  private volatile boolean isDirty;
  private volatile long mySize = -1;

  public PagedFileStorage(@NotNull Path file,
                          @Nullable StorageLockContext storageLockContext,
                          int pageSize,
                          boolean valuesAreBufferAligned,
                          boolean nativeBytesOrder) {
    myFile = file;
    // TODO read-only flag should be extracted from PersistentHashMapValueStorage.CreationTimeOptions
    myReadOnly = PersistentHashMapValueStorage.CreationTimeOptions.READONLY.get() == Boolean.TRUE;

    myStorageLockContext = lookupStorageContext(storageLockContext);
    myPageSize = Math.max(pageSize > 0 ? pageSize : DEFAULT_PAGE_SIZE, AbstractStorage.PAGE_SIZE);
    myValuesAreBufferAligned = valuesAreBufferAligned;
    myStorageIndex = myStorageLockContext.getBufferCache().registerPagedFileStorage(this);
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

  public @NotNull StorageLockContext getStorageLockContext() {
    return myStorageLockContext;
  }

  public @NotNull Path getFile() {
    return myFile;
  }

  public boolean isNativeBytesOrder() {
    return myNativeBytesOrder;
  }

  public <R> @NotNull R readInputStream(@NotNull ThrowableNotNullFunction<? super InputStream, R, ? extends IOException> consumer)
    throws IOException {
    synchronized (myInputStreamLock) {
      try {
        return executeOp(ch -> {
          ch.position(0);
          return consumer.fun(Channels.newInputStream(ch));
        }, true);
      }
      catch (NoSuchFileException ignored) {
        return consumer.fun(new ByteArrayInputStream(ArrayUtil.EMPTY_BYTE_ARRAY));
      }
    }
  }

  public <R> @NotNull R readChannel(@NotNull ThrowableNotNullFunction<? super ReadableByteChannel, R, ? extends IOException> consumer)
    throws IOException {
    synchronized (myInputStreamLock) {
      try {
        return executeOp(ch -> {
          ch.position(0);
          return consumer.fun(ch);
        }, true);
      }
      catch (NoSuchFileException ignored) {
        return consumer.fun(Channels.newChannel(new ByteArrayInputStream(ArrayUtil.EMPTY_BYTE_ARRAY)));
      }
    }
  }

  <R> R executeOp(final @NotNull FileChannelOperation<R> operation,
                  final boolean readOnly) throws IOException {
    return myStorageLockContext.executeOp(myFile, operation, readOnly);
  }

  <R> R executeIdempotentOp(final @NotNull FileChannelIdempotentOperation<R> operation,
                            final boolean readOnly) throws IOException {
    return myStorageLockContext.executeIdempotentOp(myFile, operation, readOnly);
  }

  public void putInt(long addr, int value) throws IOException {
    if (myValuesAreBufferAligned) {
      long page = addr / myPageSize;
      int page_offset = (int)(addr % myPageSize);
      DirectBufferWrapper buffer = getBuffer(page);
      try {
        buffer.putInt(page_offset, value);
      }
      finally {
        buffer.unlock();
      }
    }
    else {
      Bits.putInt(getThreadLocalTypedIOBuffer(), 0, value);
      put(addr, getThreadLocalTypedIOBuffer(), 0, 4);
    }
  }

  public int getInt(long addr) throws IOException {
    if (myValuesAreBufferAligned) {
      long page = addr / myPageSize;
      int page_offset = (int)(addr % myPageSize);
      DirectBufferWrapper buffer = getReadOnlyBuffer(page, true);
      try {
        return buffer.getInt(page_offset);
      }
      finally {
        buffer.unlock();
      }
    }
    else {
      get(addr, getThreadLocalTypedIOBuffer(), 0, 4, true);
      return Bits.getInt(getThreadLocalTypedIOBuffer(), 0);
    }
  }

  public int getOffsetInPage(long addr) {
    return (int)(addr % myPageSize);
  }

  public DirectBufferWrapper getByteBuffer(long address, boolean modify) throws IOException {
    long page = address / myPageSize;
    assert page >= 0 && page <= FilePageCache.MAX_PAGES_COUNT : address + " in " + myFile;
    return getBufferWrapper(page, modify, true);
  }

  public void putLong(long addr, long value) throws IOException {
    if (myValuesAreBufferAligned) {
      long page = addr / myPageSize;
      int page_offset = (int)(addr % myPageSize);
      DirectBufferWrapper buffer = getBuffer(page);
      try {
        buffer.putLong(page_offset, value);
      }
      finally {
        buffer.unlock();
      }
    }
    else {
      Bits.putLong(getThreadLocalTypedIOBuffer(), 0, value);
      put(addr, getThreadLocalTypedIOBuffer(), 0, 8);
    }
  }

  public long getLong(long addr) throws IOException {
    if (myValuesAreBufferAligned) {
      long page = addr / myPageSize;
      int page_offset = (int)(addr % myPageSize);
      DirectBufferWrapper buffer = getReadOnlyBuffer(page, true);
      try {
        return buffer.getLong(page_offset);
      }
      finally {
        buffer.unlock();
      }
    }
    else {
      get(addr, getThreadLocalTypedIOBuffer(), 0, 8, true);
      return Bits.getLong(getThreadLocalTypedIOBuffer(), 0);
    }
  }

  public void putBuffer(long index, @NotNull ByteBuffer data) throws IOException {
    if (!myValuesAreBufferAligned) {
      //TODO
      throw new RuntimeException("can't perform putBuffer() for unaligned storage");
    }
    long page = index / myPageSize;
    int offset = (int)(index % myPageSize);

    DirectBufferWrapper buffer = getBuffer(page);
    try {
      buffer.putFromBuffer(data, offset);
    }
    finally {
      buffer.unlock();
    }
  }

  public byte get(long index, boolean checkAccess) throws IOException {
    long page = index / myPageSize;
    int offset = (int)(index % myPageSize);

    DirectBufferWrapper buffer = getReadOnlyBuffer(page, checkAccess);
    try {
      return buffer.get(offset, checkAccess);
    }
    finally {
      buffer.unlock();
    }
  }

  public void put(long index, byte value) throws IOException {
    long page = index / myPageSize;
    int offset = (int)(index % myPageSize);

    DirectBufferWrapper buffer = getBuffer(page);
    try {
      buffer.put(offset, value);
    }
    finally {
      buffer.unlock();
    }
  }

  public void get(long index, byte[] dst, int offset, int length, boolean checkAccess) throws IOException {
    long i = index;
    int o = offset;
    int l = length;

    while (l > 0) {
      long page = i / myPageSize;
      int page_offset = (int)(i % myPageSize);

      int page_len = Math.min(l, myPageSize - page_offset);
      final DirectBufferWrapper buffer = getReadOnlyBuffer(page, checkAccess);
      try {
        buffer.readToArray(dst, o, page_offset, page_len, checkAccess);
      }
      finally {
        buffer.unlock();
      }

      l -= page_len;
      o += page_len;
      i += page_len;
    }
  }

  public void put(long index, byte[] src, int offset, int length) throws IOException {
    long i = index;
    int o = offset;
    int l = length;

    while (l > 0) {
      long page = i / myPageSize;
      int page_offset = (int)(i % myPageSize);

      int page_len = Math.min(l, myPageSize - page_offset);
      DirectBufferWrapper buffer = getBuffer(page);
      try {
        buffer.putFromArray(src, o, page_offset, page_len);
      }
      finally {
        buffer.unlock();
      }
      l -= page_len;
      o += page_len;
      i += page_len;
    }
  }

  @Override
  public void close() throws IOException {
    ExceptionUtil.runAllAndRethrowAllExceptions(
      IOException.class,
      () -> new IOException("Failed to close PagedFileStorage[" + getFile() + "]"),

      this::force,
      () -> {
        unmapAll();
        myStorageLockContext.getBufferCache().removeStorage(myStorageIndex);
        myStorageIndex = -1;
      },
      () -> {
        CHANNELS_CACHE.closeChannel(myFile);
      }
    );
  }

  private void unmapAll() {
    myStorageLockContext.getBufferCache().unmapBuffersForOwner(this);
    myLastAccessedBufferCache.clear();
  }

  public void resize(long newSize) throws IOException {
    long oldSize;

    if (Files.exists(myFile)) {
      oldSize = Files.size(myFile);
    }
    else {
      Files.createDirectories(myFile.getParent());
      oldSize = 0;
    }
    if (oldSize == newSize && oldSize == length()) {
      return;
    }

    // FileChannel.truncate doesn't modify file if the given size is greater than or equal to the file's current size,
    // and it is not guaranteed that new partition will consist of null after truncate, so we should fill it manually
    long delta = newSize - oldSize;
    mySize = -1;
    if (delta > 0) {
      myStorageLockContext.executeOp(myFile, channel -> {
        channel.write(ByteBuffer.allocate(1), newSize - 1);
        return null;
      }, false);

      mySize = newSize;
      fillWithZeros(oldSize, delta);
    }
    else {
      myStorageLockContext.executeOp(myFile, channel -> {
        channel.truncate(newSize);
        return null;
      }, false);
      mySize = newSize;
    }
  }

  private static final int MAX_FILLER_SIZE = 8192;

  private void fillWithZeros(final long from,
                             final long length) throws IOException {
    final byte[] zeroes = new byte[MAX_FILLER_SIZE];

    long offset = from;
    long remaining = length;
    while (remaining > 0) {
      final int toFill = (int)Math.min(remaining, MAX_FILLER_SIZE);
      if (toFill <= 0) {
        throw new AssertionError(
          "Bug: toFill(=" + toFill + ") -- must be positive. " +
          "Details: from: " + from + ", length: " + length + " -> offset: " + offset + ", remaining: " + remaining);
      }

      put(offset, zeroes, 0, toFill);

      remaining -= toFill;
      offset += toFill;
    }
  }

  public long length() {
    long size = mySize;
    if (size == -1) {
      if (Files.exists(myFile)) {
        try {
          mySize = size = Files.size(myFile);
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
      else {
        mySize = size = 0;
      }
    }
    return size;
  }

  private DirectBufferWrapper getBuffer(long page) throws IOException {
    return getBufferWrapper(page, true, true);
  }

  private DirectBufferWrapper getReadOnlyBuffer(long page, boolean checkAccess) throws IOException {
    return getBufferWrapper(page, false, checkAccess);
  }

  private DirectBufferWrapper getBufferWrapper(long page, boolean modify, boolean checkAccess) throws IOException {
    while (true) {
      DirectBufferWrapper wrapper = doGetBufferWrapper(page, modify, checkAccess);
      assert this == wrapper.getFile();
      if (wrapper.tryLock()) {
        return wrapper;
      }
    }
  }

  private @NotNull DirectBufferWrapper doGetBufferWrapper(long page, boolean modify, boolean checkAccess) throws IOException {
    if (myReadOnly && modify) {
      throw new IOException("Read-only storage can't be modified");
    }

    DirectBufferWrapper pageFromCache = myLastAccessedBufferCache.getPageFromCache(page);

    if (pageFromCache != null) {
      myStorageLockContext.getBufferCache().incrementFastCacheHitsCount();
      return pageFromCache;
    }

    if (page < 0 || page > FilePageCache.MAX_PAGES_COUNT) {
      throw new AssertionError("Page " + page + " is outside of [0, " + FilePageCache.MAX_PAGES_COUNT + ")");
    }

    if (myStorageIndex == -1) {
      throw new ClosedStorageException("storage is already closed; path " + myFile);
    }
    DirectBufferWrapper byteBufferWrapper =
      myStorageLockContext.getBufferCache().get(myStorageIndex | page, !modify, checkAccess); // TODO: long page

    myLastAccessedBufferCache.updateCache(page, byteBufferWrapper);

    return byteBufferWrapper;
  }

  void markDirty() {
    if (!isDirty) isDirty = true;
  }

  public void ensureCachedSizeAtLeast(long size) {
    if (mySize < size) {
      mySize = size;
    }
  }

  public boolean isReadOnly() {
    return myReadOnly;
  }

  private static byte[] getThreadLocalTypedIOBuffer() {
    return ourTypedIOBuffer.get();
  }


  @Override
  public void force() throws IOException {
    long started = IOStatistics.DEBUG ? System.currentTimeMillis() : 0;

    if (isDirty) {
      myStorageLockContext.getBufferCache().flushBuffersForOwner(this);
      isDirty = false;
    }

    if (IOStatistics.DEBUG) {
      long finished = System.currentTimeMillis();
      if (finished - started > IOStatistics.MIN_IO_TIME_TO_REPORT_MS) {
        IOStatistics.dump("Flushed " + myFile + " for " + (finished - started));
      }
    }
  }

  @Override
  public boolean isDirty() {
    return isDirty;
  }

  @Override
  public String toString() {
    return "PagedFileStorage[" + myFile + "]";
  }

  public static @NotNull StorageLockContext lookupStorageContext(@Nullable StorageLockContext storageLockContext) {
    StorageLockContext threadLocalContext = THREAD_LOCAL_STORAGE_LOCK_CONTEXT.get();
    if (threadLocalContext != null) {
      if (storageLockContext != null && storageLockContext != threadLocalContext) {
        throw new IllegalStateException("Context(" + storageLockContext + ") != THREAD_LOCAL_STORAGE_LOCK_CONTEXT(" + threadLocalContext + ")");
      }
      return threadLocalContext;
    }
    else if (storageLockContext != null) {
      return storageLockContext;
    }
    else {
      return StorageLockContext.DEFAULT_CONTEXT;
    }
  }
}
