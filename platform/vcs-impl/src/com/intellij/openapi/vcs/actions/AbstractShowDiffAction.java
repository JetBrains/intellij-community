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
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.impl.BackgroundableActionLock;
import com.intellij.openapi.vcs.impl.VcsBackgroundableActions;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.stream.Stream;

import static com.intellij.util.ObjectUtils.assertNotNull;
import static com.intellij.util.containers.UtilKt.getIfSingle;

public abstract class AbstractShowDiffAction extends AbstractVcsAction {
  @Override
  protected void update(@NotNull VcsContext vcsContext, @NotNull Presentation presentation) {
    updateDiffAction(presentation, vcsContext);
  }

  protected static void updateDiffAction(@NotNull Presentation presentation,
                                         @NotNull VcsContext vcsContext) {
    presentation.setEnabled(isEnabled(vcsContext, true));
    presentation.setVisible(isVisible(vcsContext));
  }

  protected static boolean isVisible(@NotNull VcsContext vcsContext) {
    Project project = vcsContext.getProject();
    return project != null && hasDiffProviders(project);
  }

  private static boolean hasDiffProviders(@NotNull Project project) {
    return Stream.of(ProjectLevelVcsManager.getInstance(project).getAllActiveVcss())
      .map(AbstractVcs::getDiffProvider)
      .anyMatch(Objects::nonNull);
  }

  protected static boolean isEnabled(@NotNull VcsContext vcsContext, boolean disableIfRunning) {
    Project project = vcsContext.getProject();
    if (project == null) return false;

    if (!isVisible(vcsContext)) return false;

    VirtualFile file = getIfSingle(vcsContext.getSelectedFilesStream());
    if (file == null || file.isDirectory()) return false;

    FilePath filePath = VcsUtil.getFilePath(file);

    if (disableIfRunning) {
      if (BackgroundableActionLock.isLocked(project, VcsBackgroundableActions.COMPARE_WITH, filePath)) {
        return false;
      }
    }

    AbstractVcs vcs = ChangesUtil.getVcsForFile(file, project);
    if (vcs == null || vcs.getDiffProvider() == null) return false;

    if (!AbstractVcs.fileInVcsByFileStatus(project, filePath)) return false;

    return true;
  }

  @Override
  protected void actionPerformed(@NotNull VcsContext vcsContext) {
    Project project = assertNotNull(vcsContext.getProject());

    if (!ChangeListManager.getInstance(project).isFreezedWithNotification("Can not " + vcsContext.getActionName() + " now")) {
      VirtualFile file = vcsContext.getSelectedFiles()[0];
      AbstractVcs vcs = assertNotNull(ChangesUtil.getVcsForFile(file, project));
      DiffProvider provider = assertNotNull(vcs.getDiffProvider());
      Editor editor = vcsContext.getEditor();

      getExecutor(provider, file, project, editor).showDiff();
    }
  }

  @NotNull
  protected abstract DiffActionExecutor getExecutor(@NotNull DiffProvider diffProvider,
                                                    @NotNull VirtualFile selectedFile,
                                                    @NotNull Project project,
                                                    @Nullable Editor editor);
}
