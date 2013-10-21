/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

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
  private final LinkedBlockingDeque<FileContent> myLoadedContentsQueue = new LinkedBlockingDeque<FileContent>();
  private final LinkedBlockingQueue<VirtualFile> myFilesToLoadQueue = new LinkedBlockingQueue<VirtualFile>();
  private volatile boolean myContentLoadingThreadTerminated = false;

  private volatile long myLoadedBytesInQueue;
  private final Object myProceedWithLoadingLock = new Object();

  private volatile long myBytesBeingProcessed;
  private volatile boolean myLargeSizeRequested;
  private final Object myProceedWithProcessingLock = new Object();
  private static final boolean ourAllowParallelFileReading = SystemProperties.getBooleanProperty("idea.allow.parallel.file.reading", true);

  public void queue(final Collection<VirtualFile> files, @NotNull final ProgressIndicator indicator) {
    myFilesToLoadQueue.addAll(files);
    final Runnable contentLoadingRunnable = new Runnable() {
      @Override
      public void run() {
        try {
          VirtualFile file = myFilesToLoadQueue.poll();
          while (file != null) {
            indicator.checkCanceled();
            addLast(file, indicator);
            file = myFilesToLoadQueue.poll();
          }

          // put end-of-queue marker only if not canceled
          try {
            myLoadedContentsQueue.put(new FileContent(null));
          }
          catch (InterruptedException e) {
            LOG.error(e);
          }
        }
        catch (ProcessCanceledException e) {
          // Do nothing, exit the thread.
        }
        catch (InterruptedException e) {
          LOG.error(e);
        }
        finally {
          myContentLoadingThreadTerminated = true;
        }
      }
    };

    ApplicationManager.getApplication().executeOnPooledThread(contentLoadingRunnable);
  }

  private void addLast(VirtualFile file, @NotNull final ProgressIndicator indicator) throws InterruptedException {
    FileContent content = new FileContent(file);

    if (isValidFile(file)) {
      if (!doLoadContent(content, indicator)) {
        content.setEmptyContent();
      }
    }
    else {
      content.setEmptyContent();
    }

    myLoadedContentsQueue.put(content);
  }

  private static boolean isValidFile(VirtualFile file) {
    return file.isValid() && !file.isDirectory() && !file.is(VFileProperty.SPECIAL) && !VfsUtilCore.isBrokenLink(file);
  }

  @SuppressWarnings("InstanceofCatchParameter")
  private boolean doLoadContent(final FileContent content, @NotNull final ProgressIndicator indicator) throws InterruptedException {
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
    final FileContent content = doTake();
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
  private FileContent doTake() {
    FileContent result = null;

    while (result == null) {
      if (ourAllowParallelFileReading) {
        result = myLoadedContentsQueue.poll();
        if (result == null) {
          VirtualFile virtualFileToLoad = myFilesToLoadQueue.poll();
          if (virtualFileToLoad != null) {
            FileContent content = new FileContent(virtualFileToLoad);
            if (isValidFile(virtualFileToLoad)) {
              try {
                content.getBytes();
              }
              catch (Throwable t) {
                if (t instanceof IOException || t instanceof InvalidVirtualFileAccessException) {
                  LOG.info(t);
                }
                else {
                  LOG.error(t);
                }
                content.setEmptyContent();
              }
            }
            else {
              content.setEmptyContent();
            }
            return content;
          }

          // take last content which is loaded by another thread
          do {
            try {
              result = myLoadedContentsQueue.poll(10, TimeUnit.MILLISECONDS);
              if (result != null) break;
            }
            catch (InterruptedException ex) {
              throw new RuntimeException(ex);
            }
          }
          while (!myContentLoadingThreadTerminated);
        }
      }
      else {
        try {
          result = myLoadedContentsQueue.poll(300, TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException ex) {
          throw new RuntimeException(ex);
        }
      }
      if (result == null && myContentLoadingThreadTerminated) {
        return null;
      }
    }

    final VirtualFile file = result.getVirtualFile();
    if (file == null) {
      try {
        myLoadedContentsQueue.put(result); // put it back to notify the others
      }
      catch (InterruptedException ignore) {
        // should not happen
      }
      return null;
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
    myLoadedContentsQueue.addFirst(content);
  }
}
