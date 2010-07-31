/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vfs.InvalidVirtualFileAccessException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.ArrayBlockingQueue;

/**
* @author peter
*/
@SuppressWarnings({"SynchronizeOnThis"})
public class FileContentQueue {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.startup.FileContentQueue");
  private static final long SIZE_THRESHOLD = 1024*1024;
  private long myTotalSize;

  private final ArrayBlockingQueue<FileContent> myQueue = new ArrayBlockingQueue<FileContent>(256);
  private FileContent myPushbackBuffer;

  public void queue(final Collection<VirtualFile> files, @Nullable final ProgressIndicator indicator) {
    final Runnable contentLoadingRunnable = new Runnable() {
      public void run() {
        try {
          for (VirtualFile file : files) {
            if (indicator != null) {
              indicator.checkCanceled();
            }
            put(file);
          }

          // put end-of-queue marker only if not canceled
          try {
            myQueue.put(new FileContent(null));
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
      }
    };

    ApplicationManager.getApplication().executeOnPooledThread(contentLoadingRunnable);
  }

  private void put(VirtualFile file) throws InterruptedException {
    FileContent content = new FileContent(file);

    if (file.isValid()) {
      if (!doLoadContent(content)) {
        content.setEmptyContent();
      }
    }
    else {
      content.setEmptyContent();
    }

    myQueue.put(content);
  }

  private boolean doLoadContent(final FileContent content) throws InterruptedException {
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    final long contentLength = content.getLength();

    boolean counterUpdated = false;
    try {
      if (contentLength < PersistentFS.MAX_INTELLISENSE_FILESIZE) {
        synchronized (this) {
          while (myTotalSize > SIZE_THRESHOLD) {
            if (indicator != null) {
              indicator.checkCanceled();
            }
            wait(300);
          }
          myTotalSize += contentLength;
          counterUpdated = true;
        }

        content.getBytes(); // Reads the content bytes and caches them.
      }

      return true;
    }
    catch (Throwable e) {
      if (counterUpdated) {
        synchronized (this) {
          myTotalSize -= contentLength;   // revert size counter
          notifyAll();
        }
      }
      if (e instanceof ProcessCanceledException) throw (ProcessCanceledException)e;
      if (e instanceof InterruptedException) throw (InterruptedException)e;

      if (e instanceof IOException || e instanceof InvalidVirtualFileAccessException) LOG.info(e);
      else if (ApplicationManager.getApplication().isUnitTestMode()) {
        e.printStackTrace();
      }
      else {
        LOG.error(e);
      }

      return false;
    }
  }

  public FileContent take() {
    FileContent result;
    synchronized (this) {
      if (myPushbackBuffer != null) {
        result = myPushbackBuffer;
        myPushbackBuffer = null;
        return result;
      }
    }

    try {
      result = myQueue.take();
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }

    final VirtualFile file = result.getVirtualFile();
    if (file == null) {
      return null;
    }
    if (result.getLength() < PersistentFS.MAX_INTELLISENSE_FILESIZE) {
      synchronized (this) {
        try {
          myTotalSize -= result.getLength();
        }
        finally {
          notifyAll();
        }
      }
    }

    return result;
  }

  public synchronized void pushback(@NotNull FileContent content) {
    LOG.assertTrue(myPushbackBuffer == null, "Pushback buffer is already full");
    myPushbackBuffer = content;
  }
}
