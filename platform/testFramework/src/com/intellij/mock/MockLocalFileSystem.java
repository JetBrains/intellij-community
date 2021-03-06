// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.mock;

import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.vfs.LocalFileOperationsHandler;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Set;

public class MockLocalFileSystem extends LocalFileSystem {
  private final MockVirtualFileSystem myDelegate = new MockVirtualFileSystem();

  @Override
  public void refreshIoFiles(@NotNull Iterable<? extends File> files, boolean async, boolean recursive, @Nullable Runnable onFinish) { }

  @Override
  public void refreshFiles(@NotNull Iterable<? extends VirtualFile> files, boolean async, boolean recursive, @Nullable Runnable onFinish) { }

  @NotNull
  @Override
  public Set<WatchRequest> replaceWatchedRoots(@NotNull Collection<WatchRequest> watchRequests,
                                               @Nullable Collection<String> recursiveRoots,
                                               @Nullable Collection<String> flatRoots) {
    throw new UnsupportedOperationException("Not implemented in " + getClass().getName());
  }

  @Override
  public void registerAuxiliaryFileOperationsHandler(@NotNull LocalFileOperationsHandler handler) { }

  @Override
  public void unregisterAuxiliaryFileOperationsHandler(@NotNull LocalFileOperationsHandler handler) { }

  @NotNull
  @Override
  public String getProtocol() {
    return LocalFileSystem.PROTOCOL;
  }

  @Override
  public VirtualFile findFileByPath(@NotNull String path) {
    return myDelegate.findFileByPath(path);
  }

  @Override
  public void refresh(boolean asynchronous) { }

  @Override
  public VirtualFile refreshAndFindFileByPath(@NotNull String path) {
    return findFileByPath(path);
  }

  @Override
  public void deleteFile(Object requestor, @NotNull VirtualFile vFile) { }

  @Override
  public void moveFile(Object requestor, @NotNull VirtualFile vFile, @NotNull VirtualFile newParent) { }

  @Override
  public void renameFile(Object requestor, @NotNull VirtualFile vFile, @NotNull String newName) { }

  @NotNull
  @Override
  public VirtualFile createChildFile(Object requestor, @NotNull VirtualFile vDir, @NotNull String fileName) throws IOException {
    return myDelegate.createChildFile(requestor, vDir, fileName);
  }

  @Override
  @NotNull
  public VirtualFile createChildDirectory(Object requestor, @NotNull VirtualFile vDir, @NotNull String dirName) throws IOException {
    return myDelegate.createChildDirectory(requestor, vDir, dirName);
  }

  @NotNull
  @Override
  public VirtualFile copyFile(Object requestor,
                              @NotNull VirtualFile virtualFile,
                              @NotNull VirtualFile newParent,
                              @NotNull String copyName) throws IOException {
    return myDelegate.copyFile(requestor, virtualFile, newParent, copyName);
  }

  @NotNull
  @Override
  protected String extractRootPath(@NotNull String normalizedPath) {
    return normalizedPath;
  }

  @Override
  public boolean isCaseSensitive() {
    return false;
  }

  @Override
  public VirtualFile findFileByPathIfCached(@NotNull String path) {
    return findFileByPath(path);
  }

  @Override
  public boolean exists(@NotNull VirtualFile fileOrDirectory) {
    return false;
  }

  @NotNull
  @Override
  public InputStream getInputStream(@NotNull VirtualFile file) {
    throw new UnsupportedOperationException();
  }

  @Override
  public byte @NotNull [] contentsToByteArray(@NotNull VirtualFile file) {
    return ArrayUtilRt.EMPTY_BYTE_ARRAY;
  }

  @Override
  public long getLength(@NotNull VirtualFile file) {
    return 0;
  }

  @NotNull
  @Override
  public OutputStream getOutputStream(@NotNull VirtualFile file, Object requestor, long modStamp, long timeStamp) {
    throw new UnsupportedOperationException();
  }

  @Override
  public long getTimeStamp(@NotNull VirtualFile file) {
    return 0;
  }

  @Override
  public boolean isDirectory(@NotNull VirtualFile file) {
    return false;
  }

  @Override
  public boolean isWritable(@NotNull VirtualFile file) {
    return false;
  }

  @Override
  public String @NotNull [] list(@NotNull VirtualFile file) {
    return ArrayUtilRt.EMPTY_STRING_ARRAY;
  }

  @Override
  public void setTimeStamp(@NotNull VirtualFile file, long timeStamp) { }

  @Override
  public void setWritable(@NotNull VirtualFile file, boolean writableFlag) { }

  @Override
  public int getRank() {
    return 1;
  }

  @Override
  public FileAttributes getAttributes(@NotNull VirtualFile file) {
    return null;
  }
}
