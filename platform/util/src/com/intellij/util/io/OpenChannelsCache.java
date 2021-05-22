// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.openapi.util.io.FileUtilRt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;

@ApiStatus.Internal
final class OpenChannelsCache { // TODO: Will it make sense to have a background thread, that flushes the cache by timeout?
  private final int myCacheSizeLimit;
  @NotNull
  private final Set<StandardOpenOption> myOpenOptions;
  @NotNull
  private final Map<Path, ChannelDescriptor> myCache;

  private final Object myLock = new Object();

  @FunctionalInterface
  interface ChannelProcessor<T> {
    T process(@NotNull FileChannel channel) throws IOException;
  }

  OpenChannelsCache(final int cacheSizeLimit, @NotNull Set<StandardOpenOption> openOptions) {
    myCacheSizeLimit = cacheSizeLimit;
    myOpenOptions = openOptions;
    myCache = new LinkedHashMap<>(cacheSizeLimit, 0.5f, true);
  }

  <T> T useChannel(@NotNull Path path, @NotNull ChannelProcessor<T> processor) throws IOException {
    ChannelDescriptor descriptor;
    synchronized (myLock) {
      descriptor = myCache.get(path);
      if (descriptor == null) {
        releaseOverCachedChannels();
        descriptor = new ChannelDescriptor(path, myOpenOptions);
        myCache.put(path, descriptor);
      }
      descriptor.lock();
    }

    try {
      return processor.process(descriptor.getChannel());
    } finally {
      synchronized (myLock) {
        descriptor.unlock();
      }
    }
  }

  void closeChannel(Path path) {
    synchronized (myLock) {
      final ChannelDescriptor descriptor = myCache.remove(path);

      if (descriptor != null) {
        assert !descriptor.isLocked();
        try {
          descriptor.getChannel().close();
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }
  }

  private void releaseOverCachedChannels() {
    int dropCount = myCache.size() - myCacheSizeLimit;

    if (dropCount >= 0) {
      List<Path> keysToDrop = new ArrayList<>();
      for (Map.Entry<Path, ChannelDescriptor> entry : myCache.entrySet()) {
        if (dropCount < 0) break;
        if (!entry.getValue().isLocked()) {
          dropCount--;
          keysToDrop.add(entry.getKey());
        }
      }

      for (Path file : keysToDrop) {
        closeChannel(file);
      }
    }
  }

  private static final class ChannelDescriptor {
    private int myLockCount = 0;
    private final @NotNull FileChannel myChannel;

    ChannelDescriptor(@NotNull Path file, @NotNull Set<? extends OpenOption> accessMode) throws IOException {
      myChannel = Objects.requireNonNull(FileUtilRt.doIOOperation(lastAttempt -> {
        try {
          return FileChannelUtil.unInterruptible(FileChannel.open(file, accessMode));
        }
        catch (NoSuchFileException ex) {
          Path parent = file.getParent();
          if (!Files.exists(parent)) {
            Files.createDirectories(parent);
          }
          if (!lastAttempt) return null;
          throw ex;
        }
      }));
    }

    void lock() {
      myLockCount++;
    }

    void unlock() {
      myLockCount--;
    }

    boolean isLocked() {
      return myLockCount != 0;
    }

    @NotNull FileChannel getChannel() {
      return myChannel;
    }
  }
}
