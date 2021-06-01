// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ConcurrencyUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A class intended to overcome interruptibility of {@link FileChannel}
 * by repeating passed operation until it will be successfully applied.
 *
 * If underlying {@link FileChannel} is observed in closed by interruption state,
 * in other words {@link ClosedByInterruptException} has been thrown we're trying to reopen it and apply operation again.
 */
@ApiStatus.Internal
final class UnInterruptibleFileChannelHandle {
  private static final Logger LOG = Logger.getInstance(UnInterruptibleFileChannelHandle.class);

  private final @NotNull Lock myOpenCloseLock = new ReentrantLock();
  private final @NotNull Path myPath;
  private final OpenOption @NotNull [] myOpenOptions;

  private volatile FileChannel myChannel; // null if handle has been closed

  UnInterruptibleFileChannelHandle(@NotNull Path path, OpenOption @NotNull ... openOptions) throws IOException {
    myPath = path;
    myOpenOptions = openOptions;
    reopenChannel();
  }

  <T> T executeOperation(@NotNull FileChannelIdempotentOperation<T> operation) throws IOException {
    if (!isOpen()) {
      throw new ClosedChannelException();
    }
    boolean restoreInterruption = false;
    try {
      while (true) {
        try {
          return operation.execute(myChannel);
        }
        catch (ClosedChannelException e) {
          LOG.warn("Channel " + System.identityHashCode(myChannel) + " for " + myPath + " closed. Trying to reopen it again");
          if (Thread.currentThread().isInterrupted()) {
            Thread.interrupted();
            restoreInterruption = true;
          }
          reopenChannel();
        }
      }
    }
    finally {
      if (restoreInterruption) {
        Thread.currentThread().interrupt();
      }
    }
  }

  boolean isOpen() {
    return myChannel != null;
  }

  void close() throws IOException {
    ConcurrencyUtil.withLock(myOpenCloseLock, () -> tryClose());
  }

  void reopenChannel() throws IOException {
    ConcurrencyUtil.withLock(myOpenCloseLock, () -> {
      FileChannel newFileChannel = FileChannel.open(myPath, myOpenOptions);
      try {
        tryClose();
      }
      catch (IOException ignored) {
      }
      myChannel = newFileChannel;
    });
  }

  private void tryClose() throws IOException {
    try {
      FileChannel channel = myChannel;
      if (channel != null && channel.isOpen()) {
        channel.close();
      }
    }
    finally {
      myChannel = null;
    }
  }

  interface FileChannelIdempotentOperation<T> {
    T execute(@NotNull FileChannel fileChannel) throws IOException;
  }
}
