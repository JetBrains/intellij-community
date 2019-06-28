// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl.http;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

class HttpVirtualFileImpl extends HttpVirtualFile {
  private final HttpFileSystemBase myFileSystem;
  @Nullable private final RemoteFileInfoImpl myFileInfo;
  private FileType myInitialFileType;
  private final String myPath;
  private final String myParentPath;
  private final String myName;

  private List<VirtualFile> myChildren;

  HttpVirtualFileImpl(@NotNull HttpFileSystemBase fileSystem, @Nullable HttpVirtualFileImpl parent, String path, @Nullable RemoteFileInfoImpl fileInfo) {
    if (parent != null) {
      if (parent.myChildren == null) {
        parent.myChildren = new SmartList<>();
      }
      parent.myChildren.add(this);
    }

    myFileSystem = fileSystem;
    myPath = path;
    myFileInfo = fileInfo;
    if (myFileInfo != null) {
      myFileInfo.addDownloadingListener(new FileDownloadingAdapter() {
        @Override
        public void fileDownloaded(@NotNull final VirtualFile localFile) {
          ApplicationManager.getApplication().invokeLater(() -> {
            HttpVirtualFileImpl file = HttpVirtualFileImpl.this;
            FileDocumentManager.getInstance().reloadFiles(file);
            if (!FileTypeRegistry.getInstance().isFileOfType(localFile, myInitialFileType)) {
              FileContentUtilCore.reparseFiles(file);
            }
          });
        }
      });

      path = UriUtil.trimTrailingSlashes(UriUtil.trimParameters(path));
      int lastSlash = path.lastIndexOf('/');
      if (lastSlash == -1) {
        myParentPath = null;
        myName = path;
      }
      else {
        myParentPath = path.substring(0, lastSlash);
        myName = path.substring(lastSlash + 1);
      }
    }
    else {
      int lastSlash = path.lastIndexOf('/');
      if (lastSlash == path.length() - 1) {
        myParentPath = null;
        myName = path;
      }
      else {
        int prevSlash = path.lastIndexOf('/', lastSlash - 1);
        if (prevSlash < 0) {
          myParentPath = path.substring(0, lastSlash + 1);
          myName = path.substring(lastSlash + 1);
        }
        else {
          myParentPath = path.substring(0, lastSlash);
          myName = path.substring(lastSlash + 1);
        }
      }
    }
  }

  @Override
  @Nullable
  public RemoteFileInfoImpl getFileInfo() {
    return myFileInfo;
  }

  @Override
  @NotNull
  public VirtualFileSystem getFileSystem() {
    return myFileSystem;
  }

  @NotNull
  @Override
  public String getPath() {
    return myPath;
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  public String toString() {
    return "HttpVirtualFile:" + myPath + ", info=" + myFileInfo;
  }

  @Override
  public VirtualFile getParent() {
    return myParentPath == null ? null : myFileSystem.findFileByPath(myParentPath, true);
  }

  @Override
  public boolean isWritable() {
    return false;
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public boolean isDirectory() {
    return myFileInfo == null;
  }

  @Override
  public VirtualFile[] getChildren() {
    return ContainerUtil.isEmpty(myChildren) ? EMPTY_ARRAY : myChildren.toArray(VirtualFile.EMPTY_ARRAY);
  }

  @Nullable
  @Override
  public VirtualFile findChild(@NotNull @NonNls String name) {
    if (!ContainerUtil.isEmpty(myChildren)) {
      for (VirtualFile child : myChildren) {
        if (StringUtil.equals(child.getNameSequence(), name)) {
          return child;
        }
      }
    }
    return null;
  }

  @Override
  @NotNull
  public FileType getFileType() {
    if (myFileInfo == null) {
      return super.getFileType();
    }

    VirtualFile localFile = myFileInfo.getLocalFile();
    if (localFile != null) {
      return localFile.getFileType();
    }
    FileType fileType = super.getFileType();
    if (myInitialFileType == null) {
      myInitialFileType = fileType;
    }
    return fileType;
  }

  @Override
  public InputStream getInputStream() throws IOException {
    if (myFileInfo != null) {
      VirtualFile localFile = myFileInfo.getLocalFile();
      if (localFile != null) {
        return localFile.getInputStream();
      }
    }
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull
  public OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException {
    if (myFileInfo != null) {
      VirtualFile localFile = myFileInfo.getLocalFile();
      if (localFile != null) {
        return localFile.getOutputStream(requestor, newModificationStamp, newTimeStamp);
      }
    }
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull
  public byte[] contentsToByteArray() throws IOException {
    if (myFileInfo == null) {
      throw new UnsupportedOperationException();
    }

    VirtualFile localFile = myFileInfo.getLocalFile();
    if (localFile != null) {
      return localFile.contentsToByteArray();
    }
    return ArrayUtilRt.EMPTY_BYTE_ARRAY;
  }

  @Override
  public long getTimeStamp() {
    return 0;
  }

  @Override
  public long getModificationStamp() {
    return 0;
  }

  @Override
  public long getLength() {
    return -1;
  }

  @Override
  public void refresh(final boolean asynchronous, final boolean recursive, final Runnable postRunnable) {
    if (myFileInfo != null) {
      myFileInfo.refresh(postRunnable);
    }
    else if (postRunnable != null) {
      postRunnable.run();
    }
  }
}
