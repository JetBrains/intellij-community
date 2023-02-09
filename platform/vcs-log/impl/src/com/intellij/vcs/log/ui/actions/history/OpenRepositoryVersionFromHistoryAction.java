/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.vcs.log.ui.actions.history;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.OpenSourceUtil;
import com.intellij.vcs.log.VcsCommitMetadata;
import com.intellij.vcs.log.history.FileHistoryModel;
import com.intellij.vcs.log.history.FileHistoryUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class OpenRepositoryVersionFromHistoryAction extends FileHistoryMetadataAction {

  @Override
  protected boolean isEnabled(@NotNull FileHistoryModel model, @Nullable VcsCommitMetadata detail, @NotNull AnActionEvent e) {
    if (detail != null) {
      VirtualFile file = FileHistoryUtil.createVcsVirtualFile(model.createRevision(detail));
      if (file == null) return false;
    }
    return true;
  }

  @Override
  protected void performAction(@NotNull Project project,
                               @NotNull FileHistoryModel model,
                               @NotNull VcsCommitMetadata detail,
                               @NotNull AnActionEvent e) {
    VirtualFile file = FileHistoryUtil.createVcsVirtualFile(model.createRevision(detail));
    if (file != null) {
      OpenSourceUtil.navigate(true, new OpenFileDescriptor(project, file));
    }
  }
}
