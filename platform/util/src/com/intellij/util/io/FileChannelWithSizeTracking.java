// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.intellij.util.SystemProperties.getBooleanProperty;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

/**
 * Replacement of a read-write file channel, with append-only semantics
 * Assumes file manipulations happen via this class only.
 */
final class FileChannelWithSizeTracking {
  private static final Logger LOG = Logger.getInstance(FileChannelWithSizeTracking.class);
  private static final boolean CHECK_CONSISTENCY = getBooleanProperty("idea.do.random.access.wrapper.assertions", false);

  private final Path path;
  private final ResilientFileChannel fileChannel;

  private long appendAtOffset;

  FileChannelWithSizeTracking(@NotNull Path path) throws IOException {
    final Path parent = path.getParent();
    if (!Files.exists(parent)) {
      Files.createDirectories(parent);
    }
    this.path = path;
    fileChannel = new ResilientFileChannel(path, READ, WRITE, CREATE);
    appendAtOffset = fileChannel.size();

    if (LOG.isTraceEnabled()) {
      LOG.trace("Inst:" + this + "," + Thread.currentThread() + "," + getClass().getClassLoader());
    }
  }

  /** Appends bytes at the (tracked) end-of-file */
  synchronized void append(byte @NotNull [] src, int off, int len) throws IOException {
    if (LOG.isTraceEnabled()) {
      LOG.trace("append:" + this + "," + Thread.currentThread() + "," + len + "," + appendAtOffset);
    }

    if (CHECK_CONSISTENCY) {
      assert appendAtOffset == fileChannel.size();
    }

    long offset;
    synchronized (this) {
      offset = appendAtOffset;
      appendAtOffset += len;
    }
    ByteBuffer buffer = ByteBuffer.wrap(src, off, len);
    while (buffer.hasRemaining()) {
      int written = fileChannel.write(buffer, offset);
      if (written <= 0) {
        throw new IOException("Failed to append data to " + path + ": channel made no progress");
      }
      offset += written;
    }
  }

  void read(long addr, byte[] dst, int off, int len) throws IOException {
    if (LOG.isTraceEnabled()) {
      LOG.trace("read:" + this + "," + Thread.currentThread() + "," + len + "," + addr);
    }
    fileChannel.read(ByteBuffer.wrap(dst, off, len), addr);
  }

  void close() throws IOException {
    if (LOG.isTraceEnabled()) {
      LOG.trace("Closed:" + this + "," + Thread.currentThread() );
    }
    fileChannel.close();
  }

  public void force() throws IOException {
    if (LOG.isTraceEnabled()) {
      LOG.trace("Forcing:" + this + "," + Thread.currentThread() );
    }
    fileChannel.force(true);
  }

  @Override
  public synchronized String toString() {
    return path + " [appendAt: " + appendAtOffset + "] @" + Integer.toHexString(hashCode());
  }
}
