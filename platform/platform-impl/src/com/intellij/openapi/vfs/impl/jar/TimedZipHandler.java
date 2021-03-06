// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl.jar;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.impl.ZipHandlerBase;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.hash.LinkedHashMap;
import com.intellij.util.io.ResourceHandle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.ZipFile;

/**
 * ZIP handler that keeps limited LRU number of ZipFile references open for a while after they were used.
 * Once the inactivity time is passed, the ZipFile is closed.
*/
public class TimedZipHandler extends ZipHandlerBase {
  private static final Logger LOG = Logger.getInstance(TimedZipHandler.class);
  private static final boolean doTracing = LOG.isTraceEnabled();
  private static final AtomicLong ourOpenTime = new AtomicLong();
  private static final AtomicInteger ourOpenCount = new AtomicInteger();
  private static final AtomicInteger ourCloseCount = new AtomicInteger();
  private static final AtomicLong ourCloseTime = new AtomicLong();

  private static final Map<TimedZipHandler, ScheduledFuture<?>> ourOpenFileLimitGuard;
  static {
    final int maxSize = 30;
    ourOpenFileLimitGuard = new LinkedHashMap<>(maxSize, true) {
      @Override
      protected boolean removeEldestEntry(Map.Entry<TimedZipHandler, ScheduledFuture<?>> eldest, TimedZipHandler key, ScheduledFuture<?> value) {
        if (size() > maxSize) {
          key.myHandle.invalidateZipReference(value);
          return true;
        }
        return false;
      }
    };
  }

  static void closeOpenZipReferences() {
    synchronized (ourOpenFileLimitGuard) {
      ourOpenFileLimitGuard.keySet().forEach(TimedZipHandler::dispose);
    }
  }

  private static final ScheduledExecutorService ourScheduledExecutorService =
    AppExecutorUtil.createBoundedScheduledExecutorService("Zip Handle Janitor", 1);

  private final ZipResourceHandle myHandle;

  public TimedZipHandler(@NotNull String path) {
    super(path);
    myHandle = new ZipResourceHandle(((JarFileSystemImpl)JarFileSystem.getInstance()).isMakeCopyOfJar(getFile()) ? 2000L : 5L * 60 * 1000);
  }

  @Override
  public void dispose() {
    super.dispose();
    myHandle.invalidateZipReference();
  }

  @Override
  protected @NotNull ResourceHandle<ZipFile> acquireZipHandle() throws IOException {
    myHandle.attach();
    return myHandle;
  }

  @Override
  protected long getEntryFileStamp() {
    return myHandle.getFileStamp();
  }

  private final class ZipResourceHandle extends ResourceHandle<ZipFile> {
    private final long myInvalidationTime;
    private ZipFile myFile;
    private long myFileStamp;
    private final ReentrantLock myLock = new ReentrantLock();
    private ScheduledFuture<?> myInvalidationRequest;

    private ZipResourceHandle(long invalidationTime) {
      myInvalidationTime = invalidationTime;
    }

    private void attach() throws IOException {
      synchronized (ourOpenFileLimitGuard) {
        ourOpenFileLimitGuard.remove(TimedZipHandler.this);
      }

      myLock.lock();

      try {
        ScheduledFuture<?> invalidationRequest = myInvalidationRequest;
        if (invalidationRequest != null) {
          invalidationRequest.cancel(false);
          myInvalidationRequest = null;
        }

        if (myFile == null) {
          File fileToUse = getFile();
          if (doTracing) LOG.trace("Opening: " + fileToUse);
          long t = doTracing ? System.nanoTime() : 0;
          myFileStamp = Files.getLastModifiedTime(fileToUse.toPath()).toMillis();
          ZipFile file = new ZipFile(fileToUse);
          if (doTracing) {
            t = System.nanoTime() - t;
            LOG.trace("Opened in " + TimeUnit.NANOSECONDS.toMillis(t) + "ms" +
                      ", times opened: " + ourOpenCount.incrementAndGet() +
                      ", open time: " + TimeUnit.NANOSECONDS.toMillis(ourOpenTime.addAndGet(t)) + "ms" +
                      ", reference will be cached for " + myInvalidationTime + "ms");
          }
          myFile = file;
        }
      }
      catch (Throwable e) {
        myLock.unlock();
        throw e;
      }
    }

    @Override
    public final void close() {
      assert myLock.isLocked();
      ScheduledFuture<?> invalidationRequest;
      try {
        myInvalidationRequest = invalidationRequest =
          ourScheduledExecutorService.schedule(() -> invalidateZipReference(), myInvalidationTime, TimeUnit.MILLISECONDS);
      }
      finally {
        myLock.unlock();
      }
      synchronized (ourOpenFileLimitGuard) {
        ourOpenFileLimitGuard.put(TimedZipHandler.this, invalidationRequest);
      }
    }

    @Override
    public final @NotNull ZipFile get() {
      assert myLock.isLocked();
      return myFile;
    }

    private void invalidateZipReference() {
      invalidateZipReference(null);
    }
    
    private void invalidateZipReference(@Nullable ScheduledFuture<?> expectedInvalidationRequest) {
      myLock.lock();
      try {
        if (myFile == null) return;
        if (expectedInvalidationRequest != null) {
          if (doTracing) LOG.trace("Invalidation cache size exceeded");
          if (myInvalidationRequest != expectedInvalidationRequest) {
            return;
          }
        }
        myInvalidationRequest = null;
        long t = doTracing ? System.nanoTime() : 0;
        try {
          myFile.close();
        }
        catch (IOException ex) {
          LOG.info(ex);
        }
        if (doTracing) {
          t = System.nanoTime() - t;
          LOG.trace("Closed: " + getFile() + " in " + TimeUnit.NANOSECONDS.toMillis(t) + "ms" +
                    ", times closed: " + ourCloseCount.incrementAndGet() +
                    ", close time: " + TimeUnit.NANOSECONDS.toMillis(ourCloseTime.addAndGet(t)) + "ms");
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
