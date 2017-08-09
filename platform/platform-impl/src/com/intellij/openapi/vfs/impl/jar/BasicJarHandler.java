/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vfs.impl.jar;

import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.impl.ZipHandler;
import com.intellij.util.containers.hash.LinkedHashMap;
import com.intellij.util.io.ResourceHandle;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipFile;

public class BasicJarHandler extends ZipHandler {
  private static final Logger LOG = Logger.getInstance("com.intellij.openapi.vfs.impl.jar.BasicJarHandler");
  private static final boolean doTracing = LOG.isTraceEnabled();
  private final Object myLock = new Object();
  private ScheduledFuture<?> myCachedHandleInvalidationRequest;
  private ResourceHandle<ZipFile> myCachedHandle;
  private final JarFileSystemImpl myFileSystem;
  
  public BasicJarHandler(@NotNull String path) {
    super(path);
    myFileSystem = (JarFileSystemImpl)JarFileSystem.getInstance();
  }

  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection") 
  private static final LinkedHashMap<BasicJarHandler, ScheduledFuture<?>> ourInvalidationCache;
  
  static {
    final int maxSize = 30;
    ourInvalidationCache = new LinkedHashMap<BasicJarHandler, ScheduledFuture<?>>(maxSize, true) {
      @Override
      protected boolean removeEldestEntry(Map.Entry<BasicJarHandler, ScheduledFuture<?>> eldest, BasicJarHandler key, ScheduledFuture<?> value) {
        if(size() > maxSize) {
          if (doTracing) trace("Invalidation cache size exceeded");
          key.invalidateCachedHandleIfNeeded();
          return true;
        }
        return false;
      }
    };
  } 

  @Override
  public void dispose() {
    super.dispose();

    invalidateCachedHandleIfNeeded();
  }

  private void invalidateCachedHandleIfNeeded() {
    synchronized (myLock){
      if (myCachedHandle != null) invalidateCachedHandle();
    }
  }

  private static final AtomicLong ourOpenTime = new AtomicLong();
  private static final AtomicInteger ourOpenCount = new AtomicInteger();
  private static final AtomicInteger ourCloseCount = new AtomicInteger();
  private static final AtomicLong ourCloseTime = new AtomicLong();
  @NotNull
  @Override
  protected ResourceHandle<ZipFile> acquireZipHandle() throws IOException {
    synchronized (myLock) {
      clearScheduledInvalidationRequest();

      if (myCachedHandle == null) {
        File fileToUse = getFileToUse();
        if (doTracing) trace("To be opened:" + fileToUse);
        int cacheInMs = myFileSystem.isMakeCopyOfJar(fileToUse) ? 2000 : 5 * 60 * 1000; /* 5 minute rule */
        
        long started = doTracing ? System.nanoTime() : 0;

        setFileStampAndLength(this, fileToUse.getPath());
        clearCaches();
        ZipFile file = new ZipFile(fileToUse);
        
        if (doTracing) {
          long openedFor = System.nanoTime() - started;
          int opened = ourOpenCount.incrementAndGet();
          long openTime = ourOpenTime.addAndGet(openedFor);

          trace("Opened for " +
                (openedFor / 1000000) +
                "ms, cached for " +
                cacheInMs +
                "ms, times opened:" +
                opened +
                ", open time:" +
                (openTime / 1000000) +
                "ms");
        }

        myCachedHandle = new ResourceHandle<ZipFile>(file) {
          @Override
          protected void disposeResource() {
            synchronized (myLock) {
              clearScheduledInvalidationRequest();

              myCachedHandleInvalidationRequest =
                JobScheduler.getScheduler().schedule(() -> invalidateCachedHandle(), cacheInMs, TimeUnit.MILLISECONDS);
              synchronized (ourInvalidationCache) {
                ourInvalidationCache.put(BasicJarHandler.this, myCachedHandleInvalidationRequest);
              }
            }
          }
        };
      } else {
        myCachedHandle.allocate();
      }
      
      return myCachedHandle;
    }
  }

  private void clearScheduledInvalidationRequest() {
    ScheduledFuture<?> invalidationRequest = myCachedHandleInvalidationRequest;
    if (invalidationRequest != null) {
      invalidationRequest.cancel(false);
      myCachedHandleInvalidationRequest = null;
      synchronized (ourInvalidationCache) {
        ourInvalidationCache.remove(this);
      }
    }
  }

  private void invalidateCachedHandle() {
    synchronized (myLock) {
      if (myCachedHandle == null || myCachedHandle.getRefCount() != 0) {
        return;
      }
      
      long started = doTracing ? System.nanoTime() : 0;
      try {
        myCachedHandle.get().close();
      } catch (IOException ex) {
        LOG.info(ex);
      }
      
      if (doTracing) {
        long closeTime = System.nanoTime() - started;
        int closed = ourCloseCount.incrementAndGet();
        long totalCloseTime = ourCloseTime.addAndGet(closeTime);
      
        trace("Disposed:" + getFileToUse() + " " + (closeTime / 1000000) + "ms, times closed:" + closed +
              ", closed time:" + (totalCloseTime / 1000000) + "ms");
      }
      myCachedHandle = null;
    }
  }
  
  private static void trace(String msg) {
    //System.out.println(msg);
    LOG.trace(msg);
  }
}
