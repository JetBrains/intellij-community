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
package com.intellij.mock;

import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileOperationsHandler;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Set;

/**
 * @author nik
 */
public class MockLocalFileSystem extends LocalFileSystem {
  private final MockVirtualFileSystem myDelegate = new MockVirtualFileSystem();

  @Override
  @Nullable
  public VirtualFile findFileByIoFile(@NotNull final File file) {
    return myDelegate.findFileByPath(FileUtil.toSystemIndependentName(file.getPath()));
  }

  @Override
  @Nullable
  public VirtualFile refreshAndFindFileByIoFile(@NotNull final File file) {
    return findFileByIoFile(file);
  }

  @Override
  public void refreshIoFiles(@NotNull final Iterable<File> files) {
  }

  @Override
  public void refreshFiles(@NotNull final Iterable<VirtualFile> files) {
  }

  @Override
  public void refreshIoFiles(@NotNull Iterable<File> files, boolean async, boolean recursive, @Nullable Runnable onFinish) {
  }

  @Override
  public void refreshFiles(@NotNull Iterable<VirtualFile> files, boolean async, boolean recursive, @Nullable Runnable onFinish) {
  }

  @NotNull
  @Override
  public Set<WatchRequest> replaceWatchedRoots(@NotNull Collection<WatchRequest> watchRequests,
                                               @Nullable Collection<String> recursiveRoots,
                                               @Nullable Collection<String> flatRoots) {
    throw new UnsupportedOperationException("Not implemented in " + getClass().getName());
  }

  @Override
  public void registerAuxiliaryFileOperationsHandler(@NotNull final LocalFileOperationsHandler handler) {
  }

  @Override
  public void unregisterAuxiliaryFileOperationsHandler(@NotNull final LocalFileOperationsHandler handler) {
  }


  @Override
  @NotNull
  public String getProtocol() {
    return LocalFileSystem.PROTOCOL;
  }

  @Override
  @Nullable
  public VirtualFile findFileByPath(@NotNull @NonNls final String path) {
    return myDelegate.findFileByPath(path);
  }

  @Override
  public void refresh(final boolean asynchronous) {
  }

  @Override
  @Nullable
  public VirtualFile refreshAndFindFileByPath(@NotNull final String path) {
    return findFileByPath(path);
  }

  @Override
  public void deleteFile(final Object requestor, @NotNull final VirtualFile vFile) throws IOException {
  }

  @Override
  public void moveFile(final Object requestor, @NotNull final VirtualFile vFile, @NotNull final VirtualFile newParent) throws IOException {
  }

  @Override
  public void renameFile(final Object requestor, @NotNull final VirtualFile vFile, @NotNull final String newName) throws IOException {
  }

  @NotNull
  @Override
  public VirtualFile createChildFile(final Object requestor, @NotNull final VirtualFile vDir, @NotNull final String fileName) throws IOException {
    return myDelegate.createChildFile(requestor, vDir, fileName);
  }

  @Override
  @NotNull
  public VirtualFile createChildDirectory(final Object requestor, @NotNull final VirtualFile vDir, @NotNull final String dirName) throws IOException {
    return myDelegate.createChildDirectory(requestor, vDir, dirName);
  }

  @NotNull
  @Override
  public VirtualFile copyFile(final Object requestor, @NotNull final VirtualFile virtualFile, @NotNull final VirtualFile newParent, @NotNull final String copyName)
    throws IOException {
    return myDelegate.copyFile(requestor, virtualFile, newParent, copyName);
  }

  @NotNull
  @Override
  protected String extractRootPath(@NotNull final String path) {
    return path;
  }

  @Override
  public boolean isCaseSensitive() {
    return false;
  }

  @Override
  public VirtualFile findFileByPathIfCached(@NotNull @NonNls String path) {
    return findFileByPath(path);
  }

  @Override
  public boolean exists(@NotNull final VirtualFile fileOrDirectory) {
    return false;
  }

  @Override
  @NotNull
  public InputStream getInputStream(@NotNull final VirtualFile file) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull
  public byte[] contentsToByteArray(@NotNull final VirtualFile file) throws IOException {
    return ArrayUtil.EMPTY_BYTE_ARRAY;
  }

  @Override
  public long getLength(@NotNull final VirtualFile file) {
    return 0;
  }

  @Override
  @NotNull
  public OutputStream getOutputStream(@NotNull final VirtualFile file, final Object requestor, final long modStamp, final long timeStamp) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public long getTimeStamp(@NotNull final VirtualFile file) {
    return 0;
  }

  @Override
  public boolean isDirectory(@NotNull final VirtualFile file) {
    return false;
  }

  @Override
  public boolean isWritable(@NotNull final VirtualFile file) {
    return false;
  }

  @NotNull
  @Override
  public String[] list(@NotNull final VirtualFile file) {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @Override
  public void setTimeStamp(@NotNull final VirtualFile file, final long timeStamp) throws IOException {
  }

  @Override
  public void setWritable(@NotNull final VirtualFile file, final boolean writableFlag) throws IOException {
  }

  @Override
  public int getRank() {
    return 1;
  }

  @Override
  public FileAttributes getAttributes(@NotNull VirtualFile file) {
    return null;
  }
}