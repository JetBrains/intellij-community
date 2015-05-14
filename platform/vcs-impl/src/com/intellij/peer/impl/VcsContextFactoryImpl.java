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
package com.intellij.peer.impl;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
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

/**
 * @author yole
*/
public class VcsContextFactoryImpl implements VcsContextFactory {
  public VcsContext createCachedContextOn(AnActionEvent event) {
    return VcsContextWrapper.createCachedInstanceOn(event);
  }

  public VcsContext createContextOn(final AnActionEvent event) {
    return new VcsContextWrapper(event.getDataContext(), event.getModifiers(), event.getPlace(), event.getPresentation().getText());
  }

  public FilePath createFilePathOn(@NotNull final VirtualFile virtualFile) {
    return new FilePathImpl(virtualFile);
  }

  public FilePath createFilePathOn(final File file) {
    return FilePathImpl.create(file);
  }

  public FilePath createFilePathOn(final File file, final NotNullFunction<File, Boolean> detector) {
    VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(file);
    if (virtualFile != null) {
      return createFilePathOn(virtualFile);
    }
    return createFilePathOn(file, detector.fun(file).booleanValue());
  }

  public FilePath createFilePathOn(final File file, final boolean isDirectory) {
    return FilePathImpl.create(file, isDirectory);
  }

  @NotNull
    public FilePath createFilePathOnNonLocal(final String path, final boolean isDirectory) {
    return FilePathImpl.createNonLocal(path, isDirectory);
  }

  public FilePath createFilePathOnDeleted(final File file, final boolean isDirectory) {
    return FilePathImpl.createForDeletedFile(file, isDirectory);
  }

  public FilePath createFilePathOn(final VirtualFile parent, final String name) {
    return new FilePathImpl(parent, name, false);
  }

  public LocalChangeList createLocalChangeList(Project project, @NotNull final String name) {
    return LocalChangeListImpl.createEmptyChangeListImpl(project, name);
  }
}