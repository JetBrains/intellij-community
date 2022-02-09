// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.openapi.Forceable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ThrowableNotNullFunction;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.lang.CompoundRuntimeException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class PagedFileStorage implements Forceable {
  private static final Logger LOG = Logger.getInstance(PagedFileStorage.class);
  private static final OpenChannelsCache CHANNELS_CACHE = new OpenChannelsCache(200);

  public static final int MB = 1024 * 1024;
  public static final int BUFFER_SIZE = FilePageCache.BUFFER_SIZE;

  @NotNull
  private final static ThreadLocal<byte[]> ourTypedIOBuffer = ThreadLocal.withInitial(() -> new byte[8]);
  private static final StorageLockContext ourDefaultContext = new StorageLockContext(true, false);

  @NotNull
  public static final ThreadLocal<StorageLockContext> THREAD_LOCAL_STORAGE_LOCK_CONTEXT = new ThreadLocal<>();

  @NotNull
  private final StorageLockContext myStorageLockContext;
  private final boolean myNativeBytesOrder;
  private int myStorageIndex; // -1 when closed
  @NotNull
  private final PagedFileStorageCache myLastAccessedBufferCache = new PagedFileStorageCache();

  @NotNull
  private final Path myFile;
  private final boolean myReadOnly;
  private final Object myInputStreamLock = new Object();
  protected final int myPageSize;
  protected final boolean myValuesAreBufferAligned;

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

    StorageLockContext context = THREAD_LOCAL_STORAGE_LOCK_CONTEXT.get();
    if (context != null) {
      if (storageLockContext != null && storageLockContext != context) {
        throw new IllegalStateException();
      }
      storageLockContext = context;
    }

    myStorageLockContext = storageLockContext != null ? storageLockContext : ourDefaultContext;
    myPageSize = Math.max(pageSize > 0 ? pageSize : BUFFER_SIZE, Page.PAGE_SIZE);
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

  @NotNull Path getFile() {
    return myFile;
  }

  public <R> @NotNull R readInputStream(@NotNull ThrowableNotNullFunction<? super InputStream, R, ? extends IOException> consumer) throws IOException {
    synchronized (myInputStreamLock) {
      try {
        return useChannel(ch -> {
          ch.position(0);
          return consumer.fun(Channels.newInputStream(ch));
        }, true);
      }
      catch (NoSuchFileException ignored) {
        return consumer.fun(new ByteArrayInputStream(ArrayUtil.EMPTY_BYTE_ARRAY));
      }
    }
  }

  public <R> @NotNull R readChannel(@NotNull ThrowableNotNullFunction<? super ReadableByteChannel, R, ? extends IOException> consumer) throws IOException {
    synchronized (myInputStreamLock) {
      try {
        return useChannel(ch -> {
          ch.position(0);
          return consumer.fun(ch);
        }, true);
      }
      catch (NoSuchFileException ignored) {
        return consumer.fun(Channels.newChannel(new ByteArrayInputStream(ArrayUtil.EMPTY_BYTE_ARRAY)));
      }
    }
  }

  <R> R useChannel(@NotNull OpenChannelsCache.ChannelProcessor<R> processor, boolean read) throws IOException {
    if (myStorageLockContext.useChannelCache()) {
      return CHANNELS_CACHE.useChannel(myFile, processor, read);
    }
    else {
      try (OpenChannelsCache.ChannelDescriptor desc = new OpenChannelsCache.ChannelDescriptor(myFile, read)) {
        return processor.process(desc.getChannel());
      }
    }
  }

  public void putInt(long addr, int value) throws IOException {
    if (myValuesAreBufferAligned) {
      long page = addr / myPageSize;
      int page_offset = (int)(addr % myPageSize);
      getBuffer(page).putInt(page_offset, value);
    } else {
      Bits.putInt(getThreadLocalTypedIOBuffer(), 0, value);
      put(addr, getThreadLocalTypedIOBuffer(), 0, 4);
    }
  }

  public int getInt(long addr) throws IOException {
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

  public DirectBufferWrapper getByteBuffer(long address, boolean modify) throws IOException {
    long page = address / myPageSize;
    assert page >= 0 && page <= FilePageCache.MAX_PAGES_COUNT: address + " in " + myFile;
    return getBufferWrapper(page, modify);
  }

  public void putLong(long addr, long value) throws IOException {
    if (myValuesAreBufferAligned) {
      long page = addr / myPageSize;
      int page_offset = (int)(addr % myPageSize);
      getBuffer(page).putLong(page_offset, value);
    } else {
      Bits.putLong(getThreadLocalTypedIOBuffer(), 0, value);
      put(addr, getThreadLocalTypedIOBuffer(), 0, 8);
    }
  }

  public long getLong(long addr) throws IOException {
    if (myValuesAreBufferAligned) {
      long page = addr / myPageSize;
      int page_offset = (int)(addr % myPageSize);
      return getReadOnlyBuffer(page).getLong(page_offset);
    } else {
      get(addr, getThreadLocalTypedIOBuffer(), 0, 8);
      return Bits.getLong(getThreadLocalTypedIOBuffer(), 0);
    }
  }

  public byte get(long index) throws IOException {
    long page = index / myPageSize;
    int offset = (int)(index % myPageSize);

    return getReadOnlyBuffer(page).get(offset);
  }

  public void put(long index, byte value) throws IOException {
    long page = index / myPageSize;
    int offset = (int)(index % myPageSize);

    getBuffer(page).put(offset, value);
  }

  public void get(long index, byte[] dst, int offset, int length) throws IOException {
    long i = index;
    int o = offset;
    int l = length;

    while (l > 0) {
      long page = i / myPageSize;
      int page_offset = (int) (i % myPageSize);

      int page_len = Math.min(l, myPageSize - page_offset);
      final DirectBufferWrapper buffer = getReadOnlyBuffer(page);
      buffer.readToArray(dst, o, page_offset, page_len);

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
      int page_offset = (int) (i % myPageSize);

      int page_len = Math.min(l, myPageSize - page_offset);
      final DirectBufferWrapper buffer = getBuffer(page);
      buffer.putFromArray(src, o, page_offset, page_len);
      l -= page_len;
      o += page_len;
      i += page_len;
    }
  }

  public void close() throws IOException {
    List<Exception> exceptions = new SmartList<>();
    ContainerUtil.addIfNotNull(exceptions, ExceptionUtil.runAndCatch(() -> force()));
    ContainerUtil.addIfNotNull(exceptions, ExceptionUtil.runAndCatch(() -> {
      unmapAll();
      myStorageLockContext.getBufferCache().removeStorage(myStorageIndex);
      myStorageIndex = -1;
    }));
    ContainerUtil.addIfNotNull(exceptions, ExceptionUtil.runAndCatch(() -> {
      CHANNELS_CACHE.closeChannel(myFile);
    }));
    if (!exceptions.isEmpty()) {
      throw new IOException(new CompoundRuntimeException(exceptions));
    }
  }

  private void unmapAll() {
    myStorageLockContext.getBufferCache().unmapBuffersForOwner(myStorageIndex, myStorageLockContext);
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
    if (oldSize == newSize && oldSize == length()) return;

    final long started = IOStatistics.DEBUG ? System.currentTimeMillis():0;
    myStorageLockContext.getBufferCache().invalidateBuffer(myStorageIndex | (int)(oldSize / myPageSize)); // TODO long page
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

  private void fillWithZeros(long from, long length) throws IOException {
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

  private DirectBufferWrapper getBuffer(long page) throws IOException {
    return getBufferWrapper(page, true);
  }

  private DirectBufferWrapper getReadOnlyBuffer(long page) throws IOException {
    return getBufferWrapper(page, false);
  }

  private DirectBufferWrapper getBufferWrapper(long page, boolean modify) throws IOException {
    DirectBufferWrapper pageFromCache =
      myLastAccessedBufferCache.getPageFromCache(page, myStorageLockContext.getBufferCache().getMappingChangeCount());

    if (myReadOnly && modify) {
      throw new IOException("Read-only storage can't be modified");
    }

    if (pageFromCache != null) {
      if (modify) markDirty();
      return pageFromCache;
    }

    assert page >= 0 && page <= FilePageCache.MAX_PAGES_COUNT : page;

    if (myStorageIndex == -1) {
      throw new IOException("storage is already closed; path " + myFile);
    }
    DirectBufferWrapper byteBufferWrapper = myStorageLockContext.getBufferCache().get(myStorageIndex | (int)page, !modify, myReadOnly); // TODO: long page
    if (modify) markDirty();
    if (myNativeBytesOrder) {
      byteBufferWrapper.useNativeByteOrder();
    }

    myLastAccessedBufferCache.updateCache(page, byteBufferWrapper, myStorageLockContext.getBufferCache().getMappingChangeCount());

    return byteBufferWrapper;
  }

  void markDirty() {
    if (!isDirty) isDirty = true;
  }

  private static byte[] getThreadLocalTypedIOBuffer() {
    return ourTypedIOBuffer.get();
  }

  @Override
  public void force() throws IOException {
    long started = IOStatistics.DEBUG ? System.currentTimeMillis() : 0;

    if (isDirty) {
      myStorageLockContext.getBufferCache().flushBuffersForOwner(myStorageIndex, myStorageLockContext);
      if (!myReadOnly) {
        //useChannel(ch -> {
        //  ch.force(true);
        //  return null;
        //}, false);
      }
      isDirty = false;
    }

    if (IOStatistics.DEBUG) {
      long finished = System.currentTimeMillis();
      if (finished - started > IOStatistics.MIN_IO_TIME_TO_REPORT) {
        IOStatistics.dump("Flushed " + myFile + " for " + (finished - started));
      }
    }
  }

  @Override
  public boolean isDirty() {
    return isDirty;
  }
}
