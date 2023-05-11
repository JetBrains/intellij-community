// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.file.StandardOpenOption.*;

/**
 * Replacement of read-write file channel with shadow file size.
 * Assumes file manipulations happen via this class only.
 */
final class FileChannelWithSizeTracking {
  private static final Logger LOG = Logger.getInstance(FileChannelWithSizeTracking.class);
  private static final boolean doAssertions = SystemProperties.getBooleanProperty("idea.do.random.access.wrapper.assertions", false);

  private final Path myPath;
  private final ResilientFileChannel fileChannel;

  //TODO RC: why it is volatile? It suggests mutlithreaded use, but the class itself doesn't look like it
  //         is safe for multithreaded env
  private volatile long mySize;

  FileChannelWithSizeTracking(final @NotNull Path path) throws IOException {
    final Path parent = path.getParent();
    if (!Files.exists(parent)) {
      Files.createDirectories(parent);
    }
    myPath = path;
    fileChannel = new ResilientFileChannel(path, READ, WRITE, CREATE);
    mySize = fileChannel.size();

    if (LOG.isTraceEnabled()) {
      LOG.trace("Inst:" + this + "," + Thread.currentThread() + "," + getClass().getClassLoader());
    }
  }

  long length() throws IOException {
    if (doAssertions) {
      assert mySize == fileChannel.size();
    }
    return mySize;
  }

  void write(long addr, byte[] dst, int off, int len) throws IOException {
    if (LOG.isTraceEnabled()) {
      LOG.trace("write:" + this + "," + Thread.currentThread() + "," + len + "," + addr);
    }
    int written = fileChannel.write(ByteBuffer.wrap(dst, off, len), addr);
    mySize = Math.max(written + addr, length());
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

  @Override
  public String toString() {
    return myPath + "@" + Integer.toHexString(hashCode());
  }

  public void force() throws IOException {
    if (LOG.isTraceEnabled()) {
      LOG.trace("Forcing:" + this + "," + Thread.currentThread() );
    }
    fileChannel.force(true);
  }
}
