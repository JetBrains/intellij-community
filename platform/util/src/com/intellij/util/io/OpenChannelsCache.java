// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.util.io;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

public class OpenChannelsCache { // TODO: Will it make sense to have a background thread, that flushes the cache by timeout?
  private final int myCacheSizeLimit;
  @NotNull
  private final Set<StandardOpenOption> myOpenOptions;
  @NotNull
  private final Map<Path, ChannelDescriptor> myCache;

  public OpenChannelsCache(final int cacheSizeLimit, @NotNull Set<StandardOpenOption> openOptions) {
    myCacheSizeLimit = cacheSizeLimit;
    myOpenOptions = openOptions;
    myCache = new LinkedHashMap<>(cacheSizeLimit, 0.5f, true);
  }

  public synchronized FileChannel getChannel(Path path) throws IOException {
    ChannelDescriptor descriptor = myCache.get(path);
    if (descriptor == null) {
      dropOvercache();
      descriptor = new ChannelDescriptor(path, myOpenOptions);
      myCache.put(path, descriptor);
    }
    descriptor.lock();
    return descriptor.getChannel();
  }

  private void dropOvercache() {
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

  public synchronized void releaseChannel(Path path) {
    ChannelDescriptor descriptor = myCache.get(path);
    assert descriptor != null;

    descriptor.unlock();
  }

  public synchronized void closeChannel(Path path) {
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

  private static class ChannelDescriptor {
    private int lockCount = 0;
    private final FileChannel myChannel;

    ChannelDescriptor(@NotNull Path file, @NotNull Set<? extends OpenOption> accessMode) throws IOException {
      myChannel = FileChannelUtil.unInterruptible(FileChannel.open(file, accessMode));
    }

    public void lock() {
      lockCount++;
    }

    public void unlock() {
      lockCount--;
    }

    public boolean isLocked() {
      return lockCount != 0;
    }

    public FileChannel getChannel() {
      return myChannel;
    }
  }
}
