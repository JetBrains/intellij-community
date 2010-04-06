/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.ex.http.HttpFileSystem;
import com.intellij.openapi.vfs.ex.http.HttpVirtualFileListener;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * @author nik
 */
public abstract class HttpFileSystemBase extends HttpFileSystem {
  private final String myProtocol;

  public HttpFileSystemBase(String protocol) {
    myProtocol = protocol;
  }

  public VirtualFile findFileByPath(@NotNull String path) {
    return findFileByPath(path, false);
  }

  public VirtualFile findFileByPath(@NotNull String path, boolean isDirectory) {
    try {
      String url = VirtualFileManager.constructUrl(myProtocol, path);
      return getRemoteFileManager().getOrCreateFile(url, path, isDirectory);
    }
    catch (IOException e) {
      return null;
    }
  }

  @Override
  public void addFileListener(@NotNull HttpVirtualFileListener listener) {
    getRemoteFileManager().addFileListener(listener);
  }

  @Override
  public void addFileListener(@NotNull HttpVirtualFileListener listener, @NotNull Disposable parentDisposable) {
    getRemoteFileManager().addFileListener(listener, parentDisposable);
  }

  @Override
  public void removeFileListener(@NotNull HttpVirtualFileListener listener) {
    getRemoteFileManager().removeFileListener(listener);
  }

  public boolean isFileDownloaded(@NotNull final VirtualFile file) {
    return file instanceof HttpVirtualFile && ((HttpVirtualFile)file).getFileInfo().getState() == RemoteFileState.DOWNLOADED;
  }

  public void disposeComponent() {
  }

  public void initComponent() { }

  @NotNull
  public VirtualFile createChildDirectory(Object requestor, @NotNull VirtualFile vDir, @NotNull String dirName) throws IOException {
    throw new UnsupportedOperationException();
  }

  public VirtualFile createChildFile(Object requestor, @NotNull VirtualFile vDir, @NotNull String fileName) throws IOException {
    throw new UnsupportedOperationException();
  }

  public void deleteFile(Object requestor, @NotNull VirtualFile vFile) throws IOException {
    throw new UnsupportedOperationException();
  }

  public void moveFile(Object requestor, @NotNull VirtualFile vFile, @NotNull VirtualFile newParent) throws IOException {
    throw new UnsupportedOperationException();
  }

  public VirtualFile copyFile(Object requestor, @NotNull VirtualFile vFile, @NotNull VirtualFile newParent, @NotNull final String copyName) throws IOException {
    throw new UnsupportedOperationException();
  }

  public void renameFile(Object requestor, @NotNull VirtualFile vFile, @NotNull String newName) throws IOException {
    throw new UnsupportedOperationException();
  }

  public String extractPresentableUrl(@NotNull String path) {
    return VirtualFileManager.constructUrl(myProtocol, path);
  }

  public VirtualFile refreshAndFindFileByPath(@NotNull String path) {
    return findFileByPath(path);
  }

  public void refresh(boolean asynchronous) {
  }

  @NotNull
  @Override
  public String getProtocol() {
    return myProtocol;
  }

  private static RemoteFileManagerImpl getRemoteFileManager() {
    return (RemoteFileManagerImpl)RemoteFileManager.getInstance();
  }
}
