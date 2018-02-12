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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.impl.ZipHandlerBase;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.hash.LinkedHashMap;
import com.intellij.util.io.ResourceHandle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.ZipFile;

// JarHandler that keeps limited LRU number of ZipFile references opened for a while after they were used
// Once the inactivity time passed the ZipFile is closed.
public class BasicJarHandler extends ZipHandlerBase {
  private static final Logger LOG = Logger.getInstance("com.intellij.openapi.vfs.impl.jar.BasicJarHandler");
  private static final boolean doTracing = LOG.isTraceEnabled();
  private final ZipResourceHandle myHandle;
  private final JarFileSystemImpl myFileSystem;
  
  public BasicJarHandler(@NotNull String path) {
    super(path);
    myFileSystem = (JarFileSystemImpl)JarFileSystem.getInstance();
    myHandle = new ZipResourceHandle();
  }
 
  private static final LinkedHashMap<BasicJarHandler, ScheduledFuture<?>> ourOpenFileLimitGuard;
  
  static {
    final int maxSize = 30;
    ourOpenFileLimitGuard = new LinkedHashMap<BasicJarHandler, ScheduledFuture<?>>(maxSize, true) {
      @Override
      protected boolean removeEldestEntry(Map.Entry<BasicJarHandler, ScheduledFuture<?>> eldest, BasicJarHandler key, ScheduledFuture<?> value) {
        if(size() > maxSize) {
          key.myHandle.invalidateZipReference(value);
          return true;
        }
        return false;
      }
    };
  } 

  @Override
  public void dispose() {
    super.dispose();

    myHandle.invalidateZipReference();
  }

  private static final AtomicLong ourOpenTime = new AtomicLong();
  private static final AtomicInteger ourOpenCount = new AtomicInteger();
  private static final AtomicInteger ourCloseCount = new AtomicInteger();
  private static final AtomicLong ourCloseTime = new AtomicLong();
  
  @NotNull
  @Override
  protected ResourceHandle<ZipFile> acquireZipHandle() throws IOException {
    myHandle.attach();
    return myHandle;
  }
  
  private static void trace(String msg) {
    //System.out.println(msg);
    LOG.trace(msg);
  }
  
  public static void closeOpenedZipReferences() {
    synchronized (ourOpenFileLimitGuard) {
      ourOpenFileLimitGuard.keySet().forEach(BasicJarHandler::dispose);
    }
  }

  @Override
  protected long getEntryFileStamp() {
    return myHandle.getFileStamp();
  }

  private static final ScheduledExecutorService
    ourScheduledExecutorService = AppExecutorUtil.createBoundedScheduledExecutorService("Zip handle janitor", 1);
  
  private final class ZipResourceHandle extends ResourceHandle<ZipFile> {
    private ZipFile myFile;
    private long myFileStamp;
    //private long myFileLength;
    private final ReentrantLock myLock = new ReentrantLock();
    private ScheduledFuture<?> myInvalidationRequest;
    
    void attach() throws IOException {
      synchronized (ourOpenFileLimitGuard) {
        ourOpenFileLimitGuard.remove(BasicJarHandler.this);
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
          if (doTracing) trace("To be opened:" + fileToUse);
  
          long started = doTracing ? System.nanoTime() : 0;
  
          FileAttributes attributes = FileSystemUtil.getAttributes(fileToUse.getPath());
  
          myFileStamp = attributes != null ? attributes.lastModified : DEFAULT_TIMESTAMP;
          //myFileLength = attributes != null ? attributes.length : DEFAULT_LENGTH;
  
          ZipFile file = new ZipFile(fileToUse);
  
          if (doTracing) {
            long openedFor = System.nanoTime() - started;
            int opened = ourOpenCount.incrementAndGet();
            long openTime = ourOpenTime.addAndGet(openedFor);
  
            trace("Opened for " +
                  (openedFor / 1000000) +
                  "ms, times opened:" +
                  opened +
                  ", open time:" +
                  (openTime / 1000000) +
                  "ms, reference will be cached for " + cacheInvalidationTime() + "ms");
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
    public void close() {
      assert myLock.isLocked();
      ScheduledFuture<?> invalidationRequest;
      try {
        myInvalidationRequest = invalidationRequest =
          ourScheduledExecutorService.schedule(() -> invalidateZipReference(), cacheInvalidationTime(), TimeUnit.MILLISECONDS);
      }
      finally {
        myLock.unlock();
      }

      synchronized (ourOpenFileLimitGuard) {
        ourOpenFileLimitGuard.put(BasicJarHandler.this, invalidationRequest);
      }
    }

    private int cacheInvalidationTime() {
      File file = getFile();
      return myFileSystem.isMakeCopyOfJar(file) ? 2000 : 5 * 60 * 1000;
    }

    @Override
    public ZipFile get() {
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
          if (doTracing) trace("Invalidation cache size exceeded");
          if(myInvalidationRequest != expectedInvalidationRequest) {
            return;
          }
        }
        myInvalidationRequest = null;
        long started = doTracing ? System.nanoTime() : 0;
        try {
          myFile.close();
        } catch (IOException ex) {
          LOG.info(ex);
        }

        if (doTracing) {
          long closeTime = System.nanoTime() - started;
          int closed = ourCloseCount.incrementAndGet();
          long totalCloseTime = ourCloseTime.addAndGet(closeTime);

          trace("Disposed:" + getFile() + " " + (closeTime / 1000000) + "ms, times closed:" + closed +
                ", closed time:" + (totalCloseTime / 1000000) + "ms");
        }
        myFile = null;
      } finally {
        myLock.unlock();
      }
    }

    long getFileStamp() {
      assert myLock.isLocked();
      return myFileStamp;
    }
  } 
}
