// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    super(VcsBundle.messagePointer("open.repository.version.text"), VcsBundle.messagePointer("open.repository.version.description"), null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    Change[] changes = e.getRequiredData(VcsDataKeys.SELECTED_CHANGES);
    openRepositoryVersion(project, changes);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    Change[] changes = e.getData(VcsDataKeys.SELECTED_CHANGES);
    e.getPresentation().setEnabled(project != null && changes != null &&
                                   (!CommittedChangesBrowserUseCase.IN_AIR
                                     .equals(e.getData(CommittedChangesBrowserUseCase.DATA_KEY))) &&
                                   hasValidChanges(changes) &&
                                   ModalityState.NON_MODAL.equals(ModalityState.current()));
  }

  private static boolean hasValidChanges(Change @NotNull [] changes) {
    return ContainerUtil.exists(changes, c -> c.getAfterRevision() != null && !c.getAfterRevision().getFile().isDirectory());
  }

  private static void openRepositoryVersion(@NotNull Project project, Change @NotNull [] changes) {
    for (Change change : changes) {
      ContentRevision revision = change.getAfterRevision();

      if (revision == null || revision.getFile().isDirectory()) continue;

      VirtualFile vFile = ContentRevisionVirtualFile.create(revision);
      Navigatable navigatable = new OpenFileDescriptor(project, vFile);
      navigatable.navigate(true);
    }
  }
}
