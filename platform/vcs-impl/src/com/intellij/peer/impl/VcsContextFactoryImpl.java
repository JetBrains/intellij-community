// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.peer.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.LocalFilePath;
import com.intellij.openapi.vcs.RemoteFilePath;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.LocalChangeListImpl;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Path;

public class VcsContextFactoryImpl implements VcsContextFactory {
  private static final Logger LOG = Logger.getInstance(VcsContextFactoryImpl.class);

  @Override
  public @NotNull FilePath createFilePathOn(@NotNull VirtualFile virtualFile) {
    String path = virtualFile.getPath();
    if (path.isEmpty()) {
      LOG.error(new Throwable("Invalid empty file path in " + virtualFile +
                              " (" + virtualFile.getClass().getName() + ")"));
      path = "/";
    }
    return createFilePath(path, virtualFile.isDirectory());
  }

  @Override
  public @NotNull FilePath createFilePathOn(@NotNull File file) {
    String path = file.getPath();
    VirtualFile vf = LocalFileSystem.getInstance().findFileByPath(path);
    return createFilePath(path, vf != null ? vf.isDirectory() : file.isDirectory());
  }

  @Override
  public @NotNull FilePath createFilePathOn(final @NotNull File file, final boolean isDirectory) {
    return createFilePath(file.getPath(), isDirectory);
  }

  @Override
  public @NotNull FilePath createFilePath(@NotNull Path file, boolean isDirectory) {
    return new LocalFilePath(file, isDirectory);
  }

  @Override
  public @NotNull FilePath createFilePathOnNonLocal(final @NotNull String path, final boolean isDirectory) {
    return new RemoteFilePath(path, isDirectory);
  }

  @Override
  public @NotNull FilePath createFilePathOn(final @NotNull VirtualFile parent, final @NotNull String name) {
    return createFilePath(parent, name, false);
  }

  @Override
  public @NotNull FilePath createFilePath(@NotNull VirtualFile parent, @NotNull String fileName, boolean isDirectory) {
    return createFilePath(parent.getPath() + "/" + fileName, isDirectory);
  }

  @Override
  public @NotNull LocalChangeList createLocalChangeList(@NotNull Project project, final @NotNull String name) {
    return LocalChangeListImpl.createEmptyChangeListImpl(project, name, null);
  }

  @Override
  public @NotNull FilePath createFilePath(@NotNull String path, boolean isDirectory) {
    return new LocalFilePath(path, isDirectory);
  }
}