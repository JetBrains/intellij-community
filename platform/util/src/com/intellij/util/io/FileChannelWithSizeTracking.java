// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static com.intellij.util.io.FileChannelUtil.unInterruptible;

/**
 * Replacement of read-write file channel with shadow file size, valid when file manipulations happen in with this class only.
 */
final class FileChannelWithSizeTracking {
  private static final Logger LOG = Logger.getInstance(FileChannelWithSizeTracking.class);
  private static final boolean doAssertions = SystemProperties.getBooleanProperty("idea.do.random.access.wrapper.assertions", false);

  private final Path myPath;
  private final FileChannel myChannel;
  private volatile long mySize;

  FileChannelWithSizeTracking(@NotNull Path path) throws IOException {
    Path parent = path.getParent();
    if (!Files.exists(parent)) {
      Files.createDirectories(parent);
    }
    myChannel = unInterruptible(FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE));
    mySize = myChannel.size();
    myPath = path;

    if (LOG.isTraceEnabled()) {
      LOG.trace("Inst:" + this + "," + Thread.currentThread() + "," + getClass().getClassLoader());
    }
  }

  long length() throws IOException {
    if (doAssertions) {
      assert mySize == myChannel.size();
    }
    return mySize;
  }

  void write(long addr, byte[] dst, int off, int len) throws IOException {
    if (LOG.isTraceEnabled()) {
      LOG.trace("write:" + this + "," + Thread.currentThread() + "," + len + "," + addr);
    }
    int written = myChannel.write(ByteBuffer.wrap(dst, off, len), addr);
    mySize = Math.max(written + addr, length());
  }

  void read(long addr, byte[] dst, int off, int len) throws IOException {
    if (LOG.isTraceEnabled()) {
      LOG.trace("read:" + this + "," + Thread.currentThread() + "," + len + "," + addr);
    }
    myChannel.read(ByteBuffer.wrap(dst, off, len), addr);
  }

  void close() throws IOException {
    if (LOG.isTraceEnabled()) {
      LOG.trace("Closed:" + this + "," + Thread.currentThread() );
    }
    force();
    myChannel.close();
  }

  @Override
  public String toString() {
    return myPath + "@" + Integer.toHexString(hashCode());
  }

  private void force() throws IOException {
    if (LOG.isTraceEnabled()) {
      LOG.trace("Forcing:" + this + "," + Thread.currentThread() );
    }

    myChannel.force(true);
  }
}
