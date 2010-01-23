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

package com.intellij.mock;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileOperationsHandler;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.io.fs.IFile;
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

  @Nullable
  public VirtualFile findFileByIoFile(final File file) {
    return myDelegate.findFileByPath(FileUtil.toSystemIndependentName(file.getPath()));
  }

  @Nullable
  public VirtualFile findFileByIoFile(final IFile file) {
    return myDelegate.findFileByPath(FileUtil.toSystemIndependentName(file.getPath()));
  }

  @Nullable
  public VirtualFile refreshAndFindFileByIoFile(@NotNull final File file) {
    return findFileByIoFile(file);
  }

  @Nullable
  public VirtualFile refreshAndFindFileByIoFile(final IFile ioFile) {
    return findFileByIoFile(ioFile);
  }

  public void refreshIoFiles(final Iterable<File> files) {
  }

  public void refreshFiles(final Iterable<VirtualFile> files) {
  }

  public byte[] physicalContentsToByteArray(final VirtualFile virtualFile) throws IOException {
    throw new UnsupportedOperationException("'physicalContentsToByteArray' not implemented in " + getClass().getName());
  }

  public long physicalLength(final VirtualFile virtualFile) throws IOException {
    throw new UnsupportedOperationException("'physicalLength' not implemented in " + getClass().getName());
  }

  @Nullable
  public WatchRequest addRootToWatch(final @NotNull String rootPath, final boolean toWatchRecursively) {
    throw new UnsupportedOperationException("'addRootToWatch' not implemented in " + getClass().getName());
  }

  @NotNull
  public Set<WatchRequest> addRootsToWatch(final @NotNull Collection<String> rootPaths, final boolean toWatchRecursively) {
    throw new UnsupportedOperationException("'addRootsToWatch' not implemented in " + getClass().getName());
  }

  public void removeWatchedRoots(final @NotNull Collection<WatchRequest> rootsToWatch) {
  }

  public void removeWatchedRoot(final @NotNull WatchRequest watchRequest) {
  }

  public void registerAuxiliaryFileOperationsHandler(final LocalFileOperationsHandler handler) {
  }

  public void unregisterAuxiliaryFileOperationsHandler(final LocalFileOperationsHandler handler) {
  }

  public boolean processCachedFilesInSubtree(final VirtualFile file, final Processor<VirtualFile> processor) {
    throw new UnsupportedOperationException("'processCachedFilesInSubtree' not implemented in " + getClass().getName());
  }

  @NotNull
  public String getProtocol() {
    return LocalFileSystem.PROTOCOL;
  }

  @Nullable
  public VirtualFile findFileByPath(@NotNull @NonNls final String path) {
    return myDelegate.findFileByPath(path);
  }

  public void refresh(final boolean asynchronous) {
  }

  @Nullable
  public VirtualFile refreshAndFindFileByPath(@NotNull final String path) {
    return findFileByPath(path);
  }

  public void deleteFile(final Object requestor, @NotNull final VirtualFile vFile) throws IOException {
  }

  public void moveFile(final Object requestor, @NotNull final VirtualFile vFile, @NotNull final VirtualFile newParent) throws IOException {
  }

  public void renameFile(final Object requestor, @NotNull final VirtualFile vFile, @NotNull final String newName) throws IOException {
  }

  public VirtualFile createChildFile(final Object requestor, @NotNull final VirtualFile vDir, @NotNull final String fileName) throws IOException {
    return myDelegate.createChildFile(requestor, vDir, fileName);
  }

  public VirtualFile createChildDirectory(final Object requestor, @NotNull final VirtualFile vDir, @NotNull final String dirName) throws IOException {
    return myDelegate.createChildDirectory(requestor, vDir, dirName);
  }

  public VirtualFile copyFile(final Object requestor, @NotNull final VirtualFile virtualFile, @NotNull final VirtualFile newParent, @NotNull final String copyName)
    throws IOException {
    return myDelegate.copyFile(requestor, virtualFile, newParent, copyName);
  }

  public String extractRootPath(@NotNull final String path) {
    return path;
  }

  public boolean isCaseSensitive() {
    return false;
  }

  public boolean exists(final VirtualFile fileOrDirectory) {
    return false;
  }

  @NotNull
  public InputStream getInputStream(final VirtualFile file) throws IOException {
    throw new UnsupportedOperationException();
  }

  @NotNull
  public byte[] contentsToByteArray(final VirtualFile file) throws IOException {
    return ArrayUtil.EMPTY_BYTE_ARRAY;
  }

  public long getLength(final VirtualFile file) {
    return 0;
  }

  @NotNull
  public OutputStream getOutputStream(final VirtualFile file, final Object requestor, final long modStamp, final long timeStamp) throws IOException {
    throw new UnsupportedOperationException();
  }

  public long getTimeStamp(final VirtualFile file) {
    return 0;
  }

  public boolean isDirectory(final VirtualFile file) {
    return false;
  }

  public boolean isWritable(final VirtualFile file) {
    return false;
  }

  public String[] list(final VirtualFile file) {
    return new String[0];
  }

  public VirtualFile[] listFiles(final VirtualFile file) {
    return new VirtualFile[0];
  }

  public void setTimeStamp(final VirtualFile file, final long modstamp) throws IOException {

  }

  public void setWritable(final VirtualFile file, final boolean writableFlag) throws IOException {

  }

  public int getRank() {
    return 1;
  }
}
