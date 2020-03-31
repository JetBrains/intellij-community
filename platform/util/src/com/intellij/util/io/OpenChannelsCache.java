// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.util.io;

import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OpenChannelsCache { // TODO: Will it make sense to have a background thread, that flushes the cache by timeout?
  private final int myCacheSizeLimit;
  private final String myAccessMode;
  private final Map<File, ChannelDescriptor> myCache;

  public OpenChannelsCache(final int cacheSizeLimit, @NonNls String accessMode) {
    myCacheSizeLimit = cacheSizeLimit;
    myAccessMode = accessMode;
    myCache = new LinkedHashMap<>(cacheSizeLimit, 0.5f, true);
  }

  public synchronized RandomAccessFile getChannel(File ioFile) throws FileNotFoundException {
    ChannelDescriptor descriptor = myCache.get(ioFile);
    if (descriptor == null) {
      dropOvercache();
      descriptor = new ChannelDescriptor(ioFile, myAccessMode);
      myCache.put(ioFile, descriptor);
    }
    descriptor.lock();
    return descriptor.getChannel();
  }

  private void dropOvercache() {
    int dropCount = myCache.size() - myCacheSizeLimit;

    if (dropCount >= 0) {
      List<File> keysToDrop = new ArrayList<>();
      for (Map.Entry<File, ChannelDescriptor> entry : myCache.entrySet()) {
        if (dropCount < 0) break;
        if (!entry.getValue().isLocked()) {
          dropCount--;
          keysToDrop.add(entry.getKey());
        }
      }

      for (File file : keysToDrop) {
        closeChannel(file);
      }
    }
  }

  public synchronized void releaseChannel(File ioFile) {
    ChannelDescriptor descriptor = myCache.get(ioFile);
    assert descriptor != null;

    descriptor.unlock();
  }

  public synchronized void closeChannel(File ioFile) {
    final ChannelDescriptor descriptor = myCache.remove(ioFile);

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
    private final RandomAccessFile myChannel;
    private final File myFile;

    ChannelDescriptor(File file, String accessMode) throws FileNotFoundException {
      myFile = file;
      myChannel = new RandomAccessFile(file, accessMode);
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

    public RandomAccessFile getChannel() {
      return myChannel;
    }
  }
}
