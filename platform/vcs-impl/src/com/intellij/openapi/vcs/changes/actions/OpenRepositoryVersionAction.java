/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesBrowserUseCase;
import com.intellij.openapi.vcs.vfs.ContentRevisionVirtualFile;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class OpenRepositoryVersionAction extends AnAction implements DumbAware {
  public OpenRepositoryVersionAction() {
    super(VcsBundle.message("open.repository.version.text"), VcsBundle.message("open.repository.version.description"), null);
  }

  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    Change[] changes = e.getRequiredData(VcsDataKeys.SELECTED_CHANGES);
    openRepositoryVersion(project, changes);
  }

  public void update(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    Change[] changes = e.getData(VcsDataKeys.SELECTED_CHANGES);
    e.getPresentation().setEnabled(project != null && changes != null &&
                                   (!CommittedChangesBrowserUseCase.IN_AIR
                                     .equals(CommittedChangesBrowserUseCase.DATA_KEY.getData(e.getDataContext()))) &&
                                   hasValidChanges(changes) &&
                                   ModalityState.NON_MODAL.equals(ModalityState.current()));
  }

  private static boolean hasValidChanges(@NotNull Change[] changes) {
    return ContainerUtil.exists(changes, c -> c.getAfterRevision() != null && !c.getAfterRevision().getFile().isDirectory());
  }

  private static void openRepositoryVersion(@NotNull Project project, @NotNull Change[] changes) {
    for (Change change : changes) {
      ContentRevision revision = change.getAfterRevision();

      if (revision == null || revision.getFile().isDirectory()) continue;

      VirtualFile vFile = ContentRevisionVirtualFile.create(revision);
      Navigatable navigatable = new OpenFileDescriptor(project, vFile);
      navigatable.navigate(true);
    }
  }
}
