/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.peer.impl;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.LocalFilePath;
import com.intellij.openapi.vcs.RemoteFilePath;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.actions.VcsContextWrapper;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.LocalChangeListImpl;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.NotNull;

import java.io.File;

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
    return createFilePath(virtualFile.getPath(), virtualFile.isDirectory());
  }

  @Override
  @NotNull
  public FilePath createFilePathOn(@NotNull final File file) {
    VirtualFile vf = LocalFileSystem.getInstance().findFileByIoFile(file);
    return vf != null ? createFilePathOn(vf) : createFilePath(file.getPath(), file.isDirectory());
  }

  @Override
  @NotNull
  public FilePath createFilePathOn(@NotNull final File file, @NotNull final NotNullFunction<File, Boolean> detector) {
    VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(file);
    if (virtualFile != null) {
      return createFilePathOn(virtualFile);
    }
    return createFilePathOn(file, detector.fun(file).booleanValue());
  }

  @Override
  @NotNull
  public FilePath createFilePathOn(@NotNull final File file, final boolean isDirectory) {
    return createFilePath(file.getPath(), isDirectory);
  }

  @Override
  @NotNull
  public FilePath createFilePathOnNonLocal(@NotNull final String path, final boolean isDirectory) {
    return new RemoteFilePath(path, isDirectory);
  }

  @Override
  @NotNull
  public FilePath createFilePathOnDeleted(@NotNull final File file, final boolean isDirectory) {
    return createFilePathOn(file, isDirectory);
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
    return LocalChangeListImpl.createEmptyChangeListImpl(project, name);
  }

  @NotNull
  @Override
  public FilePath createFilePath(@NotNull String path, boolean isDirectory) {
    return new LocalFilePath(path, isDirectory);
  }
}