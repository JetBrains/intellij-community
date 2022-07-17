// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.peer.impl;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.LocalFilePath;
import com.intellij.openapi.vcs.RemoteFilePath;
import com.intellij.openapi.vcs.UrlFilePath;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.actions.VcsContextWrapper;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.LocalChangeListImpl;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VersionedFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Path;

public class VcsContextFactoryImpl implements VcsContextFactory {
  @Override
  @NotNull
  public VcsContext createCachedContextOn(@NotNull AnActionEvent event) {
    return VcsContextWrapper.createCachedInstanceOn(event);
  }

  @Override
  @NotNull
  public VcsContext createContextOn(@NotNull AnActionEvent event) {
    return new VcsContextWrapper(event.getDataContext(), event.getModifiers(), event.getPlace(), event.getPresentation().getText());
  }

  @Override
  @NotNull
  public FilePath createFilePathOn(@NotNull VirtualFile virtualFile) {
    return createFilePath(virtualFile.getFileSystem() instanceof VersionedFileSystem ? virtualFile.getUrl() : virtualFile.getPath(), virtualFile.isDirectory());
  }

  @Override
  @NotNull
  public FilePath createFilePathOn(@NotNull File file) {
    String path = file.getPath();
    VirtualFile vf = LocalFileSystem.getInstance().findFileByPath(path);
    return createFilePath(path, vf != null ? vf.isDirectory() : file.isDirectory());
  }

  @Override
  @NotNull
  public FilePath createFilePathOn(@NotNull final File file, final boolean isDirectory) {
    return createFilePath(file.getPath(), isDirectory);
  }

  @Override
  public @NotNull FilePath createFilePath(@NotNull Path file, boolean isDirectory) {
    return new LocalFilePath(file, isDirectory);
  }

  @Override
  @NotNull
  public FilePath createFilePathOnNonLocal(@NotNull final String path, final boolean isDirectory) {
    return new RemoteFilePath(path, isDirectory);
  }

  @Override
  @NotNull
  public FilePath createFilePathOn(@NotNull final VirtualFile parent, @NotNull final String name) {
    return createFilePath(parent, name, false);
  }

  @NotNull
  @Override
  public FilePath createFilePath(@NotNull VirtualFile parent, @NotNull String fileName, boolean isDirectory) {
    return createFilePath(parent.getPath() + "/" + fileName, isDirectory);
  }

  @Override
  @NotNull
  public LocalChangeList createLocalChangeList(@NotNull Project project, @NotNull final String name) {
    return LocalChangeListImpl.createEmptyChangeListImpl(project, name, null);
  }

  @NotNull
  @Override
  public FilePath createFilePath(@NotNull String path, boolean isDirectory) {
    return path.contains(URLUtil.SCHEME_SEPARATOR)
           ? new UrlFilePath(path, isDirectory)
           : new LocalFilePath(path, isDirectory);
  }
}