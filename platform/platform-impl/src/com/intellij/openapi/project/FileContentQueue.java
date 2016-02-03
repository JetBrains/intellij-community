/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.project;

import com.intellij.ide.caches.FileContent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vfs.InvalidVirtualFileAccessException;
import com.intellij.openapi.vfs.VFileProperty;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SystemProperties;
import com.intellij.util.concurrency.BoundedTaskExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.PooledThreadExecutor;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author peter
 */
@SuppressWarnings({"SynchronizeOnThis"})
public class FileContentQueue {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.startup.FileContentQueue");
  private static final long MAX_SIZE_OF_BYTES_IN_QUEUE = 1024 * 1024;
  private static final long PROCESSED_FILE_BYTES_THRESHOLD = 1024 * 1024 * 3;
  private static final long LARGE_SIZE_REQUEST_THRESHOLD = PROCESSED_FILE_BYTES_THRESHOLD - 1024 * 300; // 300k for other threads

  // Unbounded (!)
  private final LinkedBlockingDeque<FileContent> myLoadedContents = new LinkedBlockingDeque<FileContent>();
  private final AtomicInteger myContentsToLoad = new AtomicInteger();

  private volatile long myLoadedBytesInQueue;
  private final Object myProceedWithLoadingLock = new Object();

  private volatile long myBytesBeingProcessed;
  private volatile boolean myLargeSizeRequested;
  private final Object myProceedWithProcessingLock = new Object();
  private static final boolean ourAllowParallelFileReading = SystemProperties.getBooleanProperty("idea.allow.parallel.file.reading", true);

  private static final BoundedTaskExecutor ourLoadingContentsExecutor = new BoundedTaskExecutor(
    PooledThreadExecutor.INSTANCE,
    ourAllowParallelFileReading ? CacheUpdateRunner.indexingThreadCount() : 1
  );

  public void queue(@NotNull Collection<VirtualFile> files, @NotNull final ProgressIndicator indicator) {
    int numberOfFiles = files.size();
    if (numberOfFiles == 0) return;
    myContentsToLoad.set(numberOfFiles);

    // ABQ is more memory efficient for significant number of files (e.g. 500K)
    final BlockingQueue<VirtualFile> filesQueue = new ArrayBlockingQueue<VirtualFile>(numberOfFiles, false, files);
    int maxFilesToBeLoadedInTheSameTime = ourAllowParallelFileReading ? CacheUpdateRunner.indexingThreadCount() : 1;

    for(int i = 0 ; i < maxFilesToBeLoadedInTheSameTime; ++i) {
      Runnable task = new Runnable() {
        @Override
        public void run() {
          VirtualFile file = filesQueue.poll();
          if (file == null) return;
          try {
            indicator.checkCanceled();
            myLoadedContents.offer(loadContent(file, indicator));
            // With loop contents of second / remaining projects will start loading only after finishing loading contents from first project.
            // With resubmit loading of contents of second/remaining projects will also proceed
            ourLoadingContentsExecutor.submit(this);
          }
          catch (ProcessCanceledException e) {
            // Do nothing, exit the thread.
          }
          catch (InterruptedException e) {
            LOG.error(e);
          }
          finally {
            myContentsToLoad.addAndGet(-1);
          }
        }
      };
      ourLoadingContentsExecutor.submit(task);
    }
  }

  private FileContent loadContent(@NotNull VirtualFile file, @NotNull final ProgressIndicator indicator) throws InterruptedException {
    FileContent content = new FileContent(file);

    if (isValidFile(file)) {
      if (!doLoadContent(content, indicator)) {
        content.setEmptyContent();
      }
    }
    else {
      content.setEmptyContent();
    }

    return content;
  }

  private static boolean isValidFile(@NotNull VirtualFile file) {
    return file.isValid() && !file.isDirectory() && !file.is(VFileProperty.SPECIAL) && !VfsUtilCore.isBrokenLink(file);
  }

  @SuppressWarnings("InstanceofCatchParameter")
  private boolean doLoadContent(@NotNull FileContent content, @NotNull final ProgressIndicator indicator) throws InterruptedException {
    final long contentLength = content.getLength();

    boolean counterUpdated = false;
    try {
      synchronized (myProceedWithLoadingLock) {
        while (myLoadedBytesInQueue > MAX_SIZE_OF_BYTES_IN_QUEUE) {
          indicator.checkCanceled();
          myProceedWithLoadingLock.wait(300);
        }
        myLoadedBytesInQueue += contentLength;
        counterUpdated = true;
      }

      content.getBytes(); // Reads the content bytes and caches them.

      return true;
    }
    catch (Throwable e) {
      if (counterUpdated) {
        synchronized (myProceedWithLoadingLock) {
          myLoadedBytesInQueue -= contentLength;   // revert size counter
        }
      }

      if (e instanceof ProcessCanceledException) {
        throw (ProcessCanceledException)e;
      }
      else if (e instanceof InterruptedException) {
        throw (InterruptedException)e;
      }
      else if (e instanceof IOException || e instanceof InvalidVirtualFileAccessException) {
        LOG.info(e);
      }
      else if (ApplicationManager.getApplication().isUnitTestMode()) {
        //noinspection CallToPrintStackTrace
        e.printStackTrace();
      }
      else {
        LOG.error(e);
      }

      return false;
    }
  }

  @Nullable
  public FileContent take(@NotNull ProgressIndicator indicator) throws ProcessCanceledException {
    final FileContent content = doTake(indicator);
    if (content == null) {
      return null;
    }
    final long length = content.getLength();
    while (true) {
      try {
        indicator.checkCanceled();
      }
      catch (ProcessCanceledException e) {
        pushback(content);
        throw e;
      }

      synchronized (myProceedWithProcessingLock) {
        final boolean requestingLargeSize = length > LARGE_SIZE_REQUEST_THRESHOLD;
        if (requestingLargeSize) {
          myLargeSizeRequested = true;
        }
        try {
          if (myLargeSizeRequested && !requestingLargeSize ||
              myBytesBeingProcessed + length > Math.max(PROCESSED_FILE_BYTES_THRESHOLD, length)) {
            myProceedWithProcessingLock.wait(300);
          }
          else {
            myBytesBeingProcessed += length;
            if (requestingLargeSize) {
              myLargeSizeRequested = false;
            }
            return content;
          }
        }
        catch (InterruptedException ignore) {
        }
      }
    }
  }

  @Nullable
  private FileContent doTake(ProgressIndicator indicator) {
    FileContent result = null;

    while (result == null) {
      try {
        int remainingContentsToLoad = myContentsToLoad.get();
        result = myLoadedContents.poll(50, TimeUnit.MILLISECONDS);
        if (result == null) {
          if (remainingContentsToLoad == 0) {
            return null;
          }
          indicator.checkCanceled();
        }
      }
      catch (InterruptedException ex) {
        throw new RuntimeException(ex);
      }
    }

    synchronized (myProceedWithLoadingLock) {
      myLoadedBytesInQueue -= result.getLength();
      if (myLoadedBytesInQueue < MAX_SIZE_OF_BYTES_IN_QUEUE) {
        myProceedWithLoadingLock
          .notifyAll(); // we actually ask only content loading thread to proceed, so there should not be much difference with plain notify
      }
    }

    return result;
  }

  public void release(@NotNull FileContent content) {
    synchronized (myProceedWithProcessingLock) {
      myBytesBeingProcessed -= content.getLength();
      myProceedWithProcessingLock.notifyAll(); // ask all sleeping threads to proceed, there can be more than one of them
    }
  }

  public void pushback(@NotNull FileContent content) {
    synchronized (myProceedWithLoadingLock) {
      myLoadedBytesInQueue += content.getLength();
    }
    myLoadedContents.addFirst(content);
  }
}
