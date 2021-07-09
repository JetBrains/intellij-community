// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.openapi.Forceable;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

public final class RandomAccessDataFile implements Forceable, Closeable {
  private static final Logger LOG = Logger.getInstance(RandomAccessDataFile.class);

  private static final OpenChannelsCache ourCache = new OpenChannelsCache(10);
  private static final AtomicInteger ourFilesCount = new AtomicInteger();

  private final int myCount = ourFilesCount.incrementAndGet();
  private final Path myFile;
  private final PagePool myPool;

  private static final ThreadLocal<byte[]> ourTypedIOBuffer = ThreadLocal.withInitial(() -> new byte[8]);

  private final OutputStreamWriter log;

  private volatile long mySize;
  private volatile boolean myIsDirty;
  private volatile boolean myIsDisposed;

  private static final boolean DEBUG = false;

  public RandomAccessDataFile(@NotNull Path file, @NotNull PagePool pool) throws IOException {
    myPool = pool;
    myFile = file;

    mySize = Files.size(file);
    if (DEBUG) {
      log = new OutputStreamWriter(Files.newOutputStream(file.getParent().resolve(file.getFileName() + ".log")), StandardCharsets.UTF_8);
    }
    else {
      log = null;
    }
  }

  public void put(long addr, byte[] bytes, int off, int len) {
    assertNotDisposed();
    ensureNonNegative(addr, "addr");
    ensureArrayBounds(bytes, off, len);

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
    ensureNonNegative(addr, "addr");
    ensureArrayBounds(bytes, off, len);

    while (len > 0) {
      final Page page = myPool.alloc(this, addr);
      int read = page.get(addr, bytes, off, len);
      len -= read;
      addr += read;
      off += read;
    }
  }

  private static void ensureArrayBounds(byte[] bytes, int off, int len) {
    if (off < 0) {
      throw new IllegalArgumentException("offset (" + off + ") should be non-negative");
    }
    if (len < 0) {
      throw new IllegalArgumentException("length (" + len + ") should be non-negative");
    }
    if (off > bytes.length) {
      throw new IllegalArgumentException("offset (" + off + ") is greater than array size (" + bytes.length + ")");
    }
    if (off + len > bytes.length) {
      throw new IllegalArgumentException("offset (" + off + ") + length (" + len + ") is greater than array size (" + bytes.length + ")");
    }
  }

  static void ensureNonNegative(long value, @NotNull String name) {
    if (value < 0) {
      throw new IllegalArgumentException(name + " should be non-negative but actual is " + value);
    }
  }

  private <T> T useFileChannel(@NotNull OpenChannelsCache.ChannelProcessor<T> channelConsumer) throws IOException {
    return ourCache.useChannel(myFile, channelConsumer, false);
  }

  public void putInt(long addr, int value) {
    Bits.putInt(ourTypedIOBuffer.get(), 0, value);
    put(addr, ourTypedIOBuffer.get(), 0, 4);
  }

  public int getInt(long addr) {
    get(addr, ourTypedIOBuffer.get(), 0, 4);
    return Bits.getInt(ourTypedIOBuffer.get(), 0);
  }

  public void putLong(long addr, long value) {
    Bits.putLong(ourTypedIOBuffer.get(), 0, value);
    put(addr, ourTypedIOBuffer.get(), 0, 8);
  }

  public long getLong(long addr) {
    get(addr, ourTypedIOBuffer.get(), 0, 8);
    return Bits.getLong(ourTypedIOBuffer.get(), 0);
  }

  public long length() {
    assertNotDisposed();
    return mySize;
  }

  public long physicalLength() {
    assertNotDisposed();

    try {
      return useFileChannel(FileChannel::size);
    }
    catch (IOException e) {
      return 0;
    }
  }

  public void dispose() {
    if (myIsDisposed) return;
    myPool.flushPages(this);
    try {
      ourCache.closeChannel(myFile);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }

    myIsDisposed = true;
  }

  @Override
  public void close() {
    dispose();
  }

  /**
   * Flushes dirty pages to underlying buffers
   */
  @Override
  public void force() {
    assertNotDisposed();
    if (isDirty()) {
      myPool.flushPages(this);
      myIsDirty = false;
    }
  }

  /**
   * Flushes dirty pages to buffers and saves them to disk
   */
  public void sync() {
    force();
    try {
      useFileChannel(ch -> {
        ch.force(true);
        return null;
      });
    }
    catch (IOException ignored) { }
  }

  public void flushSomePages(int maxPagesToFlush) {
    assertNotDisposed();
    if (isDirty()) {
      myIsDirty = !myPool.flushPages(this, maxPagesToFlush);
    }
  }

  @Override
  public boolean isDirty() {
    assertNotDisposed();
    return myIsDirty;
  }

  public boolean isDisposed() {
    return myIsDisposed;
  }

  private void assertNotDisposed() {
    if (myIsDisposed) {
      LOG.error("storage file is disposed: " + myFile);
    }
  }

  public static int totalReads;
  public static long totalReadBytes;

  public static int totalWrites;
  public static long totalWriteBytes;

  void loadPage(final Page page) {
    assertNotDisposed();
    try {
      final ByteBuffer buf = page.getBuf();

      useFileChannel(file -> {
        return file.read(ByteBuffer.wrap(buf.array(), 0, Page.PAGE_SIZE), page.getOffset());
      });

      totalReads++;
      totalReadBytes += Page.PAGE_SIZE;

      if (DEBUG) {
        log.write("Read at: \t" + page.getOffset() + "\t len: " + Page.PAGE_SIZE + ", size: " + mySize + "\n");
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  void flushPage(final Page page, int start, int end) {
    assertNotDisposed();
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

    int finalLength = length;
    useFileChannel(file -> {
      int written = file.write(ByteBuffer.wrap(buf.array(), bufOffset, finalLength), fileOffset);

      totalWrites++;
      totalWriteBytes += finalLength;

      if (DEBUG) {
        log.write("Write at: \t" + fileOffset + "\t len: " + finalLength + ", size: " + mySize + ", filesize: " + file.size() + "\n");
      }

      return written;
    });
  }

  @Override
  public int hashCode() {
    return myCount;
  }

  @Override
  public String toString() {
    return "RandomAccessFile[" + myFile + ", dirty=" + myIsDirty + "]";
  }
}
