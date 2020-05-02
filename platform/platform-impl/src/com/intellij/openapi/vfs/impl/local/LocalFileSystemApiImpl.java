// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl.local;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.LocalFileSystemApi;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;

public class LocalFileSystemApiImpl extends LocalFileSystemApi {
  private final LocalFileSystemImpl myRealFilesystem = ApplicationManager.getApplication().getService(LocalFileSystemImpl.class);

  @NotNull
  @Override
  protected LocalFileSystem getRealInstance() {
    return myRealFilesystem;
  }

  @NotNull
  @Override
  protected String extractRootPath(@NotNull String normalizedPath) {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  @Nullable
  public String normalize(@NotNull String path) {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  public @NotNull VirtualFile copyFile(Object requestor,
                                       @NotNull VirtualFile file,
                                       @NotNull VirtualFile newParent,
                                       @NotNull String copyName) {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  public @NotNull VirtualFile createChildDirectory(Object requestor, @NotNull VirtualFile parent, @NotNull String dir) {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  public @NotNull VirtualFile createChildFile(Object requestor, @NotNull VirtualFile parent, @NotNull String file) {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  public void deleteFile(Object requestor, @NotNull VirtualFile file) throws IOException {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  public void moveFile(Object requestor, @NotNull VirtualFile file, @NotNull VirtualFile newParent) throws IOException {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  public void renameFile(Object requestor, @NotNull VirtualFile file, @NotNull String newName) throws IOException {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  public @Nullable FileAttributes getAttributes(@NotNull VirtualFile file) {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  public boolean exists(@NotNull VirtualFile file) {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  public String @NotNull [] list(@NotNull VirtualFile file) {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  public boolean isDirectory(@NotNull VirtualFile file) {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  public long getTimeStamp(@NotNull VirtualFile file) {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  public void setTimeStamp(@NotNull VirtualFile file, long timeStamp) throws IOException {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  public boolean isWritable(@NotNull VirtualFile file) {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  public void setWritable(@NotNull VirtualFile file, boolean writableFlag) throws IOException {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  public byte @NotNull [] contentsToByteArray(@NotNull VirtualFile file) throws IOException {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  public @NotNull InputStream getInputStream(@NotNull VirtualFile file) throws IOException {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  public @NotNull OutputStream getOutputStream(@NotNull VirtualFile file,
                                               Object requestor,
                                               long modStamp,
                                               long timeStamp) throws IOException {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  public long getLength(@NotNull VirtualFile file) {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  public boolean isSymLink(@NotNull VirtualFile file) {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  public String resolveSymLink(@NotNull VirtualFile file) {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  public boolean markNewFilesAsDirty() {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  @NotNull
  public String getCanonicallyCasedName(@NotNull VirtualFile file) {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  public boolean hasChildren(@NotNull VirtualFile file) {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  @NotNull
  public String extractPresentableUrl(@NotNull String path) {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  public boolean isValidName(@NotNull String name) {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  @Nullable
  public Path getNioPath(@NotNull VirtualFile file) {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }
}
