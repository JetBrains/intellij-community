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
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

import static com.intellij.util.ObjectUtils.assertNotNull;
import static com.intellij.util.containers.UtilKt.getIfSingle;


public class TabbedShowHistoryAction extends AbstractVcsAction {
  @Override
  protected void update(@NotNull VcsContext context, @NotNull Presentation presentation) {
    Project project = context.getProject();

    presentation.setEnabled(isEnabled(context));
    presentation.setVisible(project != null && ProjectLevelVcsManager.getInstance(project).hasActiveVcss());
  }

  protected boolean isEnabled(@NotNull VcsContext context) {
    boolean result = false;
    Project project = context.getProject();

    if (project != null) {
      Pair<FilePath, VirtualFile> pair = getPathAndParentFile(context);

      if (pair.first != null && pair.second != null) {
        result = isEnabled(project, pair.first, pair.second);
      }
    }

    return result;
  }

  private static boolean isEnabled(@NotNull Project project, @NotNull FilePath path, @NotNull VirtualFile fileOrParent) {
    boolean result = false;
    AbstractVcs vcs = ChangesUtil.getVcsForFile(fileOrParent, project);

    if (vcs != null) {
      VcsHistoryProvider provider = vcs.getVcsHistoryProvider();

      result = provider != null &&
               (provider.supportsHistoryForDirectories() || !path.isDirectory()) &&
               AbstractVcs.fileInVcsByFileStatus(project, fileOrParent) &&
               provider.canShowHistoryFor(fileOrParent);
    }

    return result;
  }

  @NotNull
  private static Pair<FilePath, VirtualFile> getPathAndParentFile(@NotNull VcsContext context) {
    if (context.getSelectedFilesStream().findAny().isPresent()) {
      VirtualFile file = getIfSingle(context.getSelectedFilesStream());
      return file != null ? Pair.create(VcsUtil.getFilePath(file), file) : Pair.empty();
    }

    File[] ioFiles = context.getSelectedIOFiles();
    if (ioFiles != null && ioFiles.length > 0) {
      for (File ioFile : ioFiles) {
        VirtualFile parent = getParentVirtualFile(ioFile);
        if (parent != null) return Pair.create(VcsUtil.getFilePath(parent, ioFile.getName()), parent);
      }
    }

    return Pair.empty();
  }

  @Nullable
  private static VirtualFile getParentVirtualFile(@NotNull File ioFile) {
    File parentIoFile = ioFile.getParentFile();
    return parentIoFile != null ? LocalFileSystem.getInstance().findFileByIoFile(parentIoFile) : null;
  }

  @Override
  protected void actionPerformed(@NotNull VcsContext context) {
    Project project = context.getProject();
    Pair<FilePath, VirtualFile> pair = getPathAndParentFile(context);
    FilePath path = assertNotNull(pair.first);
    VirtualFile fileOrParent = assertNotNull(pair.second);
    AbstractVcs vcs = assertNotNull(ChangesUtil.getVcsForFile(fileOrParent, project));
    VcsHistoryProvider provider = assertNotNull(vcs.getVcsHistoryProvider());

    AbstractVcsHelper.getInstance(project).showFileHistory(provider, vcs.getAnnotationProvider(), path, null, vcs);
  }
}
