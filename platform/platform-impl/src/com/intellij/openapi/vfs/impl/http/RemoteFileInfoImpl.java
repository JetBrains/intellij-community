/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.vfs.impl.http;

import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsBundle;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Url;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author nik
 */
public class RemoteFileInfoImpl implements RemoteContentProvider.DownloadingCallback, RemoteFileInfo {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.impl.http.RemoteFileInfoImpl");
  private final Object myLock = new Object();
  private final Url myUrl;
  private final RemoteFileManagerImpl myManager;
  private @Nullable RemoteContentProvider myContentProvider;
  private File myLocalFile;
  private VirtualFile myLocalVirtualFile;
  private VirtualFile myPrevLocalFile;
  private RemoteFileState myState = RemoteFileState.DOWNLOADING_NOT_STARTED;
  private String myErrorMessage;
  private final AtomicBoolean myCancelled = new AtomicBoolean();
  private final List<FileDownloadingListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  public RemoteFileInfoImpl(final @NotNull Url url, final @NotNull RemoteFileManagerImpl manager) {
    myUrl = url;
    myManager = manager;
  }

  @Override
  public void addDownloadingListener(@NotNull FileDownloadingListener listener) {
    myListeners.add(listener);
  }

  @Override
  public void removeDownloadingListener(final @NotNull FileDownloadingListener listener) {
    myListeners.remove(listener);
  }

  @Override
  public void restartDownloading() {
    synchronized (myLock) {
      myErrorMessage = null;
      myPrevLocalFile = myLocalVirtualFile;
      myLocalVirtualFile = null;
      myState = RemoteFileState.DOWNLOADING_NOT_STARTED;
      myLocalFile = null;
      startDownloading();
    }
  }

  @Override
  public void startDownloading() {
    LOG.debug("Downloading requested");

    File localFile;
    synchronized (myLock) {
      if (myState != RemoteFileState.DOWNLOADING_NOT_STARTED) {
        LOG.debug("File already downloaded: file = " + myLocalVirtualFile + ", state = " + myState);
        return;
      }
      myState = RemoteFileState.DOWNLOADING_IN_PROGRESS;

      try {
        myLocalFile = myManager.getStorage().createLocalFile(myUrl);
        LOG.debug("Local file created: " + myLocalFile.getAbsolutePath());
      }
      catch (IOException e) {
        LOG.info(e);
        errorOccurred(VfsBundle.message("cannot.create.local.file", e.getMessage()), false);
        return;
      }
      myCancelled.set(false);
      localFile = myLocalFile;
    }
    for (FileDownloadingListener listener : myListeners) {
      listener.downloadingStarted();
    }

    if (myContentProvider == null) {
      myContentProvider = myManager.findContentProvider(myUrl);
    }
    myContentProvider.saveContent(myUrl, localFile, this);
  }

  @Override
  public void finished(@Nullable final FileType fileType) {
    final File localIOFile;

    synchronized (myLock) {
      LOG.debug("Downloading finished, size = " + myLocalFile.length() + ", file type=" + (fileType != null ? fileType.getName() : "null"));
      if (fileType != null) {
        String fileName = myLocalFile.getName();
        int dot = fileName.lastIndexOf('.');
        String extension = fileType.getDefaultExtension();
        if (dot == -1 || !extension.regionMatches(true, 0, fileName, dot + 1, extension.length())) {
          File newFile = FileUtil.findSequentNonexistentFile(myLocalFile.getParentFile(), fileName, extension);
          try {
            FileUtil.rename(myLocalFile, newFile);
            myLocalFile = newFile;
          }
          catch (IOException e) {
            LOG.debug(e);
          }
        }
      }

      localIOFile = myLocalFile;
    }

    VirtualFile localFile = new WriteAction<VirtualFile>() {
      @Override
      protected void run(@NotNull final Result<VirtualFile> result) {
        final VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(localIOFile);
        if (file != null) {
          file.refresh(false, false);
        }
        result.setResult(file);
      }
    }.execute().getResultObject();
    LOG.assertTrue(localFile != null, "Virtual local file not found for " + localIOFile.getAbsolutePath());
    LOG.debug("Virtual local file: " + localFile + ", size = " + localFile.getLength());
    synchronized (myLock) {
      myLocalVirtualFile = localFile;
      myPrevLocalFile = null;
      myState = RemoteFileState.DOWNLOADED;
      myErrorMessage = null;
    }
    for (FileDownloadingListener listener : myListeners) {
      listener.fileDownloaded(localFile);
    }
  }

  @Override
  public boolean isCancelled() {
    return myCancelled.get();
  }

  @Override
  public String getErrorMessage() {
    synchronized (myLock) {
      return myErrorMessage;
    }
  }

  @Override
  public void errorOccurred(@NotNull final String errorMessage, boolean cancelled) {
    LOG.debug("Error: " + errorMessage);
    synchronized (myLock) {
      myLocalVirtualFile = null;
      myPrevLocalFile = null;
      myState = RemoteFileState.ERROR_OCCURRED;
      myErrorMessage = errorMessage;
    }
    for (FileDownloadingListener listener : myListeners) {
      if (!cancelled) {
        listener.errorOccurred(errorMessage);
      }
    }
  }

  @Override
  public void setProgressFraction(final double fraction) {
    for (FileDownloadingListener listener : myListeners) {
      listener.progressFractionChanged(fraction);
    }
  }

  @Override
  public void setProgressText(@NotNull final String text, final boolean indeterminate) {
    for (FileDownloadingListener listener : myListeners) {
      listener.progressMessageChanged(indeterminate, text);
    }
  }

  @Override
  public VirtualFile getLocalFile() {
    synchronized (myLock) {
      return myLocalVirtualFile;
    }
  }

  @Override
  public String toString() {
    final String errorMessage = getErrorMessage();
    return "state=" + getState()
           + ", local file=" + myLocalFile
           + (errorMessage != null ? ", error=" + errorMessage : "")
           + (isCancelled() ? ", cancelled" : "");
  }

  @Override
  public RemoteFileState getState() {
    synchronized (myLock) {
      return myState;
    }
  }

  @Override
  public void cancelDownloading() {
    synchronized (myLock) {
      myCancelled.set(true);
      if (myPrevLocalFile != null) {
        myLocalVirtualFile = myPrevLocalFile;
        myLocalFile = VfsUtilCore.virtualToIoFile(myLocalVirtualFile);
        myState = RemoteFileState.DOWNLOADED;
        myErrorMessage = null;
      }
      else {
        myState = RemoteFileState.ERROR_OCCURRED;
      }
    }
    for (FileDownloadingListener listener : myListeners) {
      listener.downloadingCancelled();
    }
  }

  public void refresh(final @Nullable Runnable postRunnable) {
    VirtualFile localVirtualFile;
    synchronized (myLock) {
      localVirtualFile = myLocalVirtualFile;
    }
    final RemoteContentProvider contentProvider = myManager.findContentProvider(myUrl);
    if ((localVirtualFile == null || !contentProvider.equals(myContentProvider) || !contentProvider.isUpToDate(myUrl, localVirtualFile))) {
      myContentProvider = contentProvider;
      addDownloadingListener(new MyRefreshingDownloadingListener(postRunnable));
      restartDownloading();
    }
  }

  private class MyRefreshingDownloadingListener extends FileDownloadingAdapter {
    private final Runnable myPostRunnable;

    public MyRefreshingDownloadingListener(final Runnable postRunnable) {
      myPostRunnable = postRunnable;
    }

    @Override
    public void downloadingCancelled() {
      removeDownloadingListener(this);
      if (myPostRunnable != null) {
        myPostRunnable.run();
      }
    }

    @Override
    public void fileDownloaded(final VirtualFile localFile) {
      removeDownloadingListener(this);
      if (myPostRunnable != null) {
        myPostRunnable.run();
      }
    }

    @Override
    public void errorOccurred(@NotNull final String errorMessage) {
      removeDownloadingListener(this);
      if (myPostRunnable != null) {
        myPostRunnable.run();
      }
    }
  }

  @NotNull
  public Promise<VirtualFile> download() {
    synchronized (myLock) {
      switch (getState()) {
        case DOWNLOADING_NOT_STARTED:
          startDownloading();
          return createDownloadedCallback(this);
        case DOWNLOADING_IN_PROGRESS:
          return createDownloadedCallback(this);
        case DOWNLOADED:
          return Promise.resolve(myLocalVirtualFile);

        case ERROR_OCCURRED:
        default:
          return Promise.reject("errorOccured");
      }
    }
  }

  @NotNull
  private static Promise<VirtualFile> createDownloadedCallback(@NotNull final RemoteFileInfo remoteFileInfo) {
    final AsyncPromise<VirtualFile> promise = new AsyncPromise<>();
    remoteFileInfo.addDownloadingListener(new FileDownloadingAdapter() {
      @Override
      public void fileDownloaded(VirtualFile localFile) {
        try {
          remoteFileInfo.removeDownloadingListener(this);
        }
        finally {
          promise.setResult(localFile);
        }
      }

      @Override
      public void errorOccurred(@NotNull String errorMessage) {
        try {
          remoteFileInfo.removeDownloadingListener(this);
        }
        finally {
          promise.setError(errorMessage);
        }
      }

      @Override
      public void downloadingCancelled() {
        try {
          remoteFileInfo.removeDownloadingListener(this);
        }
        finally {
          promise.setError("Cancelled");
        }
      }
    });
    return promise;
  }
}
