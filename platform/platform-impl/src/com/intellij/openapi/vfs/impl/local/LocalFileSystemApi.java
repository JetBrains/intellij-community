// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl.local;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.vfs.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;

/**
 * We have two roles behind the {@link LocalFileSystem} class so far.
 * The first role - is an API entry point to exchange some local files
 * with a {@link VirtualFile}. Secondly, it is implementation of the
 * filesystem itself used from {@link VirtualFile} implementations.
 * <br />
 * We'd like to provide a transparent {@link VirtualFileLookupService}
 * to implement all major VirtualFile lookup needs from one hand. From
 * the other hand, we'd like to separate platform service from the
 * filesystem implementation.
 * <br />
 * This class is a fake implementation of the {@link LocalFileSystem}
 * that delegates to the right underlying services for most of the methods.
 * It throws exceptions when {@link VirtualFile} specific APIs are called
 * directly. This class must not be used with
 * {@link com.intellij.openapi.vfs.newvfs.VfsImplUtil} or any other vfs-impl classes.
 * <br />
 * @implNote Calling base methods that are not overridden here will likely
 * throw an Non-Implemented exception as we keep only API methods here,
 * where as all {@link VirtualFile} implementation related methods are
 * not listed
 */
public final class LocalFileSystemApi extends LocalFileSystem {
  private final LocalFileSystemImpl myRealFilesystem = ApplicationManager.getApplication().getService(LocalFileSystemImpl.class);

  @NotNull
  private LocalFileSystem getRealInstance() {
    return myRealFilesystem;
  }

  @Nullable
  @Override
  public VirtualFile findFileByPathIfCached(@NotNull String path) {
    return VirtualFileLookup.newLookup().onlyIfCached().fromPath(path);
  }

  @Nullable
  @Override
  public VirtualFile findFileByPath(@NotNull String path) {
    return VirtualFileLookup.newLookup().fromPath(path);
  }

  @Nullable
  @Override
  public VirtualFile refreshAndFindFileByPath(@NotNull String path) {
    return VirtualFileLookup.newLookup().withRefresh().fromPath(path);
  }

  @Nullable
  @Override
  public VirtualFile findFileByIoFile(@NotNull File file) {
    return VirtualFileLookup.newLookup().fromIoFile(file);
  }

  @Nullable
  @Override
  public VirtualFile refreshAndFindFileByIoFile(@NotNull File file) {
    return VirtualFileLookup.newLookup().withRefresh().fromIoFile(file);
  }

  @Override
  public void refreshIoFiles(@NotNull Iterable<? extends File> files, boolean async, boolean recursive, @Nullable Runnable onFinish) {
    getRealInstance().refreshIoFiles(files, async, recursive, onFinish);
  }

  @Override
  public void refreshFiles(@NotNull Iterable<? extends VirtualFile> files, boolean async, boolean recursive, @Nullable Runnable onFinish) {
    getRealInstance().refreshFiles(files, async, recursive, onFinish);
  }

  @NotNull
  @Override
  public Set<WatchRequest> replaceWatchedRoots(@NotNull Collection<WatchRequest> watchRequests,
                                               @Nullable Collection<String> recursiveRoots,
                                               @Nullable Collection<String> flatRoots) {
    return getRealInstance().replaceWatchedRoots(watchRequests, recursiveRoots, flatRoots);
  }

  @Override
  public void registerAuxiliaryFileOperationsHandler(@NotNull LocalFileOperationsHandler handler) {
    getRealInstance().registerAuxiliaryFileOperationsHandler(handler);
  }

  @Override
  public void unregisterAuxiliaryFileOperationsHandler(@NotNull LocalFileOperationsHandler handler) {
    getRealInstance().unregisterAuxiliaryFileOperationsHandler(handler);
  }

  @Override
  public int getRank() {
    return getRealInstance().getRank();
  }

  @NotNull
  @Override
  public String getProtocol() {
    return getRealInstance().getProtocol();
  }

  @Override
  public void refresh(boolean asynchronous) {
    getRealInstance().refresh(asynchronous);
  }

  @Override
  public void refreshWithoutFileWatcher(boolean asynchronous) {
    getRealInstance().refreshWithoutFileWatcher(asynchronous);
  }

  @Override
  public boolean isReadOnly() {
    return getRealInstance().isReadOnly();
  }

  @Override
  public boolean isCaseSensitive() {
    return getRealInstance().isCaseSensitive();
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
  public void deleteFile(Object requestor, @NotNull VirtualFile file) {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  public void moveFile(Object requestor, @NotNull VirtualFile file, @NotNull VirtualFile newParent) {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  public void renameFile(Object requestor, @NotNull VirtualFile file, @NotNull String newName) {
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
  public void setTimeStamp(@NotNull VirtualFile file, long timeStamp) {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  public boolean isWritable(@NotNull VirtualFile file) {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  public void setWritable(@NotNull VirtualFile file, boolean writableFlag) {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  public byte @NotNull [] contentsToByteArray(@NotNull VirtualFile file) {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  public @NotNull InputStream getInputStream(@NotNull VirtualFile file) {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  public @NotNull OutputStream getOutputStream(@NotNull VirtualFile file,
                                               Object requestor,
                                               long modStamp,
                                               long timeStamp) {
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
