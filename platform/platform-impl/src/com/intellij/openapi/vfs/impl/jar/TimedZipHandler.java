// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl.jar;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.impl.GenericZipFile;
import com.intellij.openapi.vfs.impl.ZipHandlerBase;
import com.intellij.util.io.ResourceHandle;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.ZipFile;

import static com.intellij.util.concurrency.AppExecutorUtil.createBoundedScheduledExecutorService;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * ZIP handler that keeps a limited number ({@linkplain #LRU_CACHE_SIZE}) of {@link ZipFile} references open for a while
 * ({@linkplain #RETENTION_MS}) after they were used.
 * Once the inactivity time is passed, the file is closed.
 */
public final class TimedZipHandler extends ZipHandlerBase {
  private static final Logger LOG = Logger.getInstance(TimedZipHandler.class);

  /** How much time to wait after {@linkplain ZipResourceHandle#close()} and actual zip file close */
  private static final long RETENTION_MS = 2000L;
  /** Max handlers to keep open (for a while, {@linkplain #RETENTION_MS}) */
  private static final int LRU_CACHE_SIZE = 30;

  /**
   * Cache of recently used handlers, limited-size ({@linkplain #LRU_CACHE_SIZE}).
   * The oldest (least-recently-used) entry is the first one to drop (=close).
   * The handlersLRUCache itself is used to protect from multithreaded access.
   */
  private static final Object2ObjectLinkedOpenHashMap<TimedZipHandler, ScheduledFuture<?>> handlersLRUCache =
    new Object2ObjectLinkedOpenHashMap<>(LRU_CACHE_SIZE + 1);
  /** Executor for delayed ({@linkplain #RETENTION_MS}) zipFile closing */
  private static final ScheduledExecutorService postponedInvalidationsExecutor =
    createBoundedScheduledExecutorService("Zip Handle Janitor", 1);

  @ApiStatus.Internal
  public static void closeOpenZipReferences() {
    List<Pair<TimedZipHandler, ScheduledFuture<?>>> entriesToClearAndCancel = new ArrayList<>();
    synchronized (handlersLRUCache) {
      for (Map.Entry<TimedZipHandler, ScheduledFuture<?>> entry : handlersLRUCache.entrySet()) {
        entriesToClearAndCancel.add(Pair.createNonNull(entry.getKey(), entry.getValue()));
      }
      handlersLRUCache.clear();
    }
    //Avoid invoking .clearCaches() under handlersLRUCache lock: handler's instance myLock shouldn't be acquired inside
    // handlersLRUCache
    for (var entry : entriesToClearAndCancel) {
      TimedZipHandler handler = entry.first;
      handler.clearCaches();
      ScheduledFuture<?> invalidationRequest = entry.second;
      invalidationRequest.cancel(false);
    }
  }

  private final ZipResourceHandle handle = new ZipResourceHandle();

  public TimedZipHandler(@NotNull String path) {
    super(path);
  }

  @Override
  public void clearCaches() {
    super.clearCaches();
    handle.invalidateZipReference(null);
  }

  @Override
  protected @NotNull ResourceHandle<GenericZipFile> acquireZipHandle() throws IOException {
    handle.attach();
    return handle;
  }

  @Override
  protected long getEntryFileStamp() {
    return handle.getFileStamp();
  }


  private final class ZipResourceHandle extends ResourceHandle<GenericZipFile> {

    /** all fields protected by this lock */
    private final ReentrantLock lock = new ReentrantLock();

    //Assumption: we assume the GenericZipFile implementations are _thread-safe_ by themselves, so this handle doesn't need
    //            to guard ZipFile. I.e. this class keeps its state changes thread-safe, but doesn't care about ZipFile
    //            thread-safety.

    private GenericZipFile zipFile;
    /** zip file last-modified-timestamp, ms */
    private long lastModifiedMs;

    /**
     * Postponed request to close zipFile after some period of not-use ({@linkplain #RETENTION_MS}).
     * Cleaning of zipFiles is doing 2-folds: by timeout, and by cache-size.
     * Timeout is not 100% bullet-prove, because it could be the handler is still in use at the moment {@linkplain #RETENTION_MS}
     * is elapsed -- those handlers will be closed by cache-size, in next .close() call.
     */
    private ScheduledFuture<?> scheduledInvalidationRequest = null;

    private int referenceCount = 0;

    private void attach() throws IOException {
      synchronized (handlersLRUCache) {
        handlersLRUCache.remove(TimedZipHandler.this);
      }

      lock.lock();
      try {
        if (zipFile != null) {
          cancelPostponedInvalidation();
        }
        else {
          Path path = getPath();
          zipFile = getZipFileWrapper(path);
          lastModifiedMs = Files.getLastModifiedTime(path).toMillis();
        }
        referenceCount++;
      }
      finally {
        lock.unlock();
      }
    }


    @Override
    public void close() {
      lock.lock();
      try {
        if (zipFile == null || referenceCount == 0) {
          return; //nothing to close
        }

        referenceCount--;

        ScheduledFuture<?> invalidationRequest = schedulePostponedInvalidation();

        synchronized (handlersLRUCache) {
          handlersLRUCache.putAndMoveToFirst(TimedZipHandler.this, invalidationRequest);
        }
      }
      finally {
        lock.unlock();
      }

      //if already too many cached entries -> invalidate (close) the oldest (least-recently-used):
      for (int i = 0; i < LRU_CACHE_SIZE; i++) {
        //^^^ loop iterations must be limited because it could be too many handlers are in use thus can't be closed right now
        TimedZipHandler leastUsedHandler;
        ScheduledFuture<?> invalidationRequest;
        synchronized (handlersLRUCache) {
          if (handlersLRUCache.size() <= LRU_CACHE_SIZE) {
            break;
          }

          leastUsedHandler = handlersLRUCache.lastKey();
          invalidationRequest = handlersLRUCache.removeLast();
        }

        if (leastUsedHandler != null) {
          leastUsedHandler.handle.invalidateZipReference(invalidationRequest);
        }
      }
    }

    //@GuardedBy(lock)
    private @NotNull ScheduledFuture<?> schedulePostponedInvalidation() {
      cancelPostponedInvalidation();

      ScheduledFuture<?> invalidationRequest = postponedInvalidationsExecutor.schedule(
        () -> invalidateZipReference( /*expectedRequest: */ null),
        RETENTION_MS,
        MILLISECONDS
      );
      this.scheduledInvalidationRequest = invalidationRequest;
      return invalidationRequest;
    }

    //@GuardedBy(lock)
    private void cancelPostponedInvalidation() {
      assert lock.isHeldByCurrentThread() : "Lock must be acquired before this method invocation";
      if (scheduledInvalidationRequest != null) {
        scheduledInvalidationRequest.cancel(false);
        scheduledInvalidationRequest = null;
      }
    }

    /**
     * @param expectedInvalidationRequest if not-null, then only invalidate (close) zip file if state.invalidationRequest==expected,
     *                                    if null -- invalidate (close) regardless of current state.invalidationRequest value
     */
    private void invalidateZipReference(@Nullable ScheduledFuture<?> expectedInvalidationRequest) {
      //We must NOT close zip file if it is in use right now, which is that referenceCount is for.
      // Solution: reference-counting, + re-schedule the timer if referenceCount>0 at current timer expiration
      GenericZipFile zipFileLocalCopy;
      lock.lock();
      try {
        if (referenceCount > 0) {
          //MAYBE RC: if there is a scheduledInvalidationRequest -> cancel it and re-schedule?
          return;
        }

        if (zipFile == null
            || expectedInvalidationRequest != null && scheduledInvalidationRequest != expectedInvalidationRequest) {
          return; // (already closed) OR (the handler is re-acquired)
        }

        cancelPostponedInvalidation();
        zipFileLocalCopy = zipFile;
        zipFile = null;
      }
      finally {
        lock.unlock();
      }

      //close file outside the lock to reduce time-under-lock:
      try {
        zipFileLocalCopy.close();
      }
      catch (IOException e) {
        LOG.warn(e);
      }
    }

    @Override
    public @NotNull GenericZipFile get() {
      lock.lock();
      try {
        if (zipFile == null) {
          throw new IllegalStateException("Handler is closed");
        }
        return zipFile;
      }
      finally {
        lock.unlock();
      }
    }

    private long getFileStamp() {
      lock.lock();
      try {
        if (zipFile == null) {
          throw new IllegalStateException("Handler is closed");
        }
        return lastModifiedMs;
      }
      finally {
        lock.unlock();
      }
    }
  }
}
