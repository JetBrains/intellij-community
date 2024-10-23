// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl.jar;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.impl.*;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.io.ResourceHandle;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.ZipFile;

/**
 * ZIP handler that keeps a limited number of {@link ZipFile} references open for a while after they were used.
 * Once the inactivity time is passed, the file is closed.
*/
public final class TimedZipHandler extends ZipHandlerBase {
  private static final Logger LOG = Logger.getInstance(TimedZipHandler.class);

  private static final long RETENTION_MS = 2000L;
  private static final int LRU_CACHE_SIZE = 30;
  @SuppressWarnings("SSBasedInspection")
  private static final Object2ObjectLinkedOpenHashMap<TimedZipHandler, ScheduledFuture<?>> ourLRUCache =
    new Object2ObjectLinkedOpenHashMap<>(LRU_CACHE_SIZE + 1);
  private static final ScheduledExecutorService ourScheduledExecutorService =
    AppExecutorUtil.createBoundedScheduledExecutorService("Zip Handle Janitor", 1);

  @ApiStatus.Internal
  public static void closeOpenZipReferences() {
    synchronized (ourLRUCache) {
      for (var handler : ourLRUCache.keySet()) {
        handler.clearCaches();
      }
      ourLRUCache.clear();
    }
  }

  private final ZipResourceHandle myHandle;

  public TimedZipHandler(@NotNull String path) {
    super(path);
    myHandle = new ZipResourceHandle();
  }

  @Override
  public void clearCaches() {
    super.clearCaches();
    myHandle.invalidateZipReference(null);
  }

  @Override
  protected @NotNull ResourceHandle<GenericZipFile> acquireZipHandle() throws IOException {
    myHandle.attach();
    return myHandle;
  }

  @Override
  protected long getEntryFileStamp() {
    return myHandle.getFileStamp();
  }

  private final class ZipResourceHandle extends ResourceHandle<GenericZipFile> {
    private GenericZipFile myFile;
    private long myFileStamp;
    private final ReentrantLock myLock = new ReentrantLock();
    private ScheduledFuture<?> myInvalidationRequest;

    private void attach() throws IOException {
      synchronized (ourLRUCache) {
        ourLRUCache.remove(TimedZipHandler.this);
      }

      myLock.lock();

      try {
        if (myInvalidationRequest != null) {
          myInvalidationRequest.cancel(false);
          myInvalidationRequest = null;
        }
        if (myFile == null) {
          var file = getFile();
          myFileStamp = Files.getLastModifiedTime(file.toPath()).toMillis();
          // see com.intellij.openapi.vfs.impl.ZipHandler
          if (ZipHandler.isFileLikelyLocal(file)) {
            myFile = new JavaZipFileWrapper(file);
          }
          else {
            myFile = new JBZipFileWrapper(file);
          }
        }
      }
      catch (Throwable t) {
        myLock.unlock();
        throw t;
      }
    }

    @Override
    public void close() {
      assert myLock.isLocked();

      ScheduledFuture<?> invalidationRequest;
      try {
        myInvalidationRequest = invalidationRequest =
          ourScheduledExecutorService.schedule(() -> { invalidateZipReference(null); }, RETENTION_MS, TimeUnit.MILLISECONDS);
      }
      finally {
        myLock.unlock();
      }

      TimedZipHandler leastUsedHandler = null;
      synchronized (ourLRUCache) {
        if (ourLRUCache.putAndMoveToFirst(TimedZipHandler.this, invalidationRequest) == null && ourLRUCache.size() > LRU_CACHE_SIZE) {
          leastUsedHandler = ourLRUCache.lastKey();
          invalidationRequest = ourLRUCache.removeLast();
        }
      }
      if (leastUsedHandler != null) {
        leastUsedHandler.myHandle.invalidateZipReference(invalidationRequest);
      }
    }

    @Override
    public @NotNull GenericZipFile get() {
      assert myLock.isLocked();
      return myFile;
    }

    // `expectedInvalidationRequest` is not null when dropping out of `ourLRUCache`
    private void invalidateZipReference(@Nullable ScheduledFuture<?> expectedInvalidationRequest) {
      myLock.lock();
      try {
        if (myFile == null || expectedInvalidationRequest != null && myInvalidationRequest != expectedInvalidationRequest) {
          return; // already closed || the handler is re-acquired
        }
        if (myInvalidationRequest != null) {
          myInvalidationRequest.cancel(false);
          myInvalidationRequest = null;
        }
        try {
          myFile.close();
        }
        catch (IOException e) {
          LOG.info(e);
        }
        myFile = null;
      }
      finally {
        myLock.unlock();
      }
    }

    private long getFileStamp() {
      assert myLock.isLocked();
      return myFileStamp;
    }
  } 
}
