// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.io.stats.CachedChannelsStatistics;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;

@ApiStatus.Internal
final class OpenChannelsCache { // TODO: Will it make sense to have a background thread, that flushes the cache by timeout?
  private final int myCapacity;
  private int myHitCount;
  private int myMissCount;
  private int myLoadCount;

  @NotNull
  private final Map<Path, ChannelDescriptor> myCache;

  private final Object myLock = new Object();

  OpenChannelsCache(final int capacity) {
    myCapacity = capacity;
    myCache = new LinkedHashMap<>(capacity, 0.5f, true);
  }

  @NotNull CachedChannelsStatistics getStatistics() {
    synchronized (myLock) {
      return new CachedChannelsStatistics(myHitCount, myMissCount, myLoadCount, myCapacity);
    }
  }

  @FunctionalInterface
  interface ChannelProcessor<T> {
    T process(@NotNull FileChannel channel) throws IOException;
  }

  /**
   * Parameter {@param processor} should be idempotent because sometimes calculation might be restarted
   * when file channel was closed by thread interruption
   */
  <T> T useChannel(@NotNull Path path,
                   @NotNull ChannelProcessor<T> processor,
                   boolean read) throws IOException {
    ChannelDescriptor descriptor;
    synchronized (myLock) {
      descriptor = myCache.get(path);
      if (descriptor == null) {
        boolean somethingDropped = releaseOverCachedChannels();
        descriptor = new ChannelDescriptor(path, read);
        myCache.put(path, descriptor);
        if (somethingDropped) {
          myMissCount++;
        }
        else {
          myLoadCount++;
        }
      }
      else if (!read && descriptor.isReadOnly()) {
        if (descriptor.isLocked()) {
          descriptor = new ChannelDescriptor(path, false);
        }
        else {
          // re-open as write
          closeChannel(path);
          descriptor = new ChannelDescriptor(path, false);
          myCache.put(path, descriptor);
        }
        myMissCount++;
      }
      else {
        myHitCount++;
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

  void closeChannel(Path path) throws IOException {
    synchronized (myLock) {
      final ChannelDescriptor descriptor = myCache.remove(path);

      if (descriptor != null) {
        assert !descriptor.isLocked();
        descriptor.close();
      }
    }
  }

  private boolean releaseOverCachedChannels() throws IOException {
    int dropCount = myCache.size() - myCapacity;

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

      return true;
    }
    return false;
  }

  static final class ChannelDescriptor implements Closeable {
    private int myLockCount = 0;
    private final @NotNull UnInterruptibleFileChannel myChannel;
    private final boolean myReadOnly;

    private static final OpenOption[] MODIFIABLE_OPTS = {StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE};
    private static final OpenOption[] READ_ONLY_OPTS = {StandardOpenOption.READ};

    ChannelDescriptor(@NotNull Path file, boolean readOnly) throws IOException {
      myReadOnly = readOnly;
      myChannel = Objects.requireNonNull(FileUtilRt.doIOOperation(lastAttempt -> {
        try {
          return new UnInterruptibleFileChannel(file, readOnly ? READ_ONLY_OPTS : MODIFIABLE_OPTS);
        }
        catch (NoSuchFileException ex) {
          Path parent = file.getParent();
          if (!readOnly) {
            if (!Files.exists(parent)) {
              Files.createDirectories(parent);
            }
            if (!lastAttempt) return null;
          }
          throw ex;
        }
      }));
    }

    boolean isReadOnly() {
      return myReadOnly;
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

    @NotNull UnInterruptibleFileChannel getChannel() {
      return myChannel;
    }

    @Override
    public void close() throws IOException {
      myChannel.close();
    }
  }
}
