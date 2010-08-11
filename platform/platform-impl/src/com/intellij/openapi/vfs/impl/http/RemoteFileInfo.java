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
package com.intellij.openapi.vfs.impl.http;

import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsBundle;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author nik
 */
public class RemoteFileInfo implements RemoteContentProvider.DownloadingCallback {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.impl.http.RemoteFileInfo");
  private final Object myLock = new Object();
  private final String myUrl;
  private final RemoteFileManagerImpl myManager;
  private @Nullable RemoteContentProvider myContentProvider;
  private File myLocalFile;
  private VirtualFile myLocalVirtualFile;
  private VirtualFile myPrevLocalFile;
  private RemoteFileState myState = RemoteFileState.DOWNLOADING_NOT_STARTED;
  private String myErrorMessage;
  private final AtomicBoolean myCancelled = new AtomicBoolean();
  private final List<FileDownloadingListener> myListeners = new SmartList<FileDownloadingListener>();

  public RemoteFileInfo(final @NotNull String url, final @NotNull RemoteFileManagerImpl manager) {
    myUrl = url;
    myManager = manager;
  }

  public void addDownloadingListener(@NotNull FileDownloadingListener listener) {
    synchronized (myLock) {
      myListeners.add(listener);
    }
  }

  public void removeDownloadingListener(final @NotNull FileDownloadingListener listener) {
    synchronized (myLock) {
      myListeners.remove(listener);
    }
  }

  public String getUrl() {
    return myUrl;
  }

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

  public void startDownloading() {
    LOG.debug("Downloading requested");

    File localFile;
    FileDownloadingListener[] listeners;
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
      listeners = myListeners.toArray(new FileDownloadingListener[myListeners.size()]);
    }
    for (FileDownloadingListener listener : listeners) {
      listener.downloadingStarted();
    }

    if (myContentProvider == null) {
      myContentProvider = myManager.findContentProvider(myUrl);
    }
    myContentProvider.saveContent(myUrl, localFile, this);
  }

  public void finished(@Nullable final FileType fileType) {
    final File localIOFile;

    synchronized (myLock) {
      LOG.debug("Downloading finished, size = " + myLocalFile.length() + ", file type=" + (fileType != null ? fileType.getName() : "null"));
      if (fileType != null) {
        String fileName = myLocalFile.getName();
        int dot = fileName.lastIndexOf('.');
        String extension = fileType.getDefaultExtension();
        if (dot == -1 || !extension.equals(fileName.substring(dot + 1))) {
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
      protected void run(final Result<VirtualFile> result) {
        final VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(localIOFile);
        if (file != null) {
          file.refresh(false, false);
        }
        result.setResult(file);
      }
    }.execute().getResultObject();
    LOG.assertTrue(localFile != null, "Virtual local file not found for " + localIOFile.getAbsolutePath());
    LOG.debug("Virtual local file: " + localFile + ", size = " + localFile.getLength());
    FileDownloadingListener[] listeners;
    synchronized (myLock) {
      myLocalVirtualFile = localFile;
      myPrevLocalFile = null;
      myState = RemoteFileState.DOWNLOADED;
      myErrorMessage = null;
      listeners = myListeners.toArray(new FileDownloadingListener[myListeners.size()]);
    }
    for (FileDownloadingListener listener : listeners) {
      listener.fileDownloaded(localFile);
    }
  }

  public boolean isCancelled() {
    return myCancelled.get();
  }

  public String getErrorMessage() {
    synchronized (myLock) {
      return myErrorMessage;
    }
  }

  public void errorOccurred(@NotNull final String errorMessage, boolean cancelled) {
    LOG.debug("Error: " + errorMessage);
    FileDownloadingListener[] listeners;
    synchronized (myLock) {
      myLocalVirtualFile = null;
      myPrevLocalFile = null;
      myState = RemoteFileState.ERROR_OCCURRED;
      myErrorMessage = errorMessage;
      listeners = myListeners.toArray(new FileDownloadingListener[myListeners.size()]);
    }
    for (FileDownloadingListener listener : listeners) {
      if (!cancelled) {
        listener.errorOccurred(errorMessage);
      }
    }
  }

  public void setProgressFraction(final double fraction) {
    FileDownloadingListener[] listeners;
    synchronized (myLock) {
      listeners = myListeners.toArray(new FileDownloadingListener[myListeners.size()]);
    }
    for (FileDownloadingListener listener : listeners) {
      listener.progressFractionChanged(fraction);
    }
  }

  public void setProgressText(@NotNull final String text, final boolean indeterminate) {
    FileDownloadingListener[] listeners;
    synchronized (myLock) {
      listeners = myListeners.toArray(new FileDownloadingListener[myListeners.size()]);
    }
    for (FileDownloadingListener listener : listeners) {
      listener.progressMessageChanged(indeterminate, text);
    }
  }

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

  public RemoteFileState getState() {
    synchronized (myLock) {
      return myState;
    }
  }

  public void cancelDownloading() {
    FileDownloadingListener[] listeners;
    synchronized (myLock) {
      myCancelled.set(true);
      if (myPrevLocalFile != null) {
        myLocalVirtualFile = myPrevLocalFile;
        myLocalFile = VfsUtil.virtualToIoFile(myLocalVirtualFile);
        myState = RemoteFileState.DOWNLOADED;
        myErrorMessage = null;
      }
      else {
        myState = RemoteFileState.ERROR_OCCURRED;
      }
      listeners = myListeners.toArray(new FileDownloadingListener[myListeners.size()]);
    }
    for (FileDownloadingListener listener : listeners) {
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
}
