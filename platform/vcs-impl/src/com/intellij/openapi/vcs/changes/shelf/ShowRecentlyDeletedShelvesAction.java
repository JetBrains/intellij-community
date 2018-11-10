// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public class ShowRecentlyDeletedShelvesAction extends DumbAwareAction {

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    final Project project = e.getData(CommonDataKeys.PROJECT);
    final Presentation presentation = e.getPresentation();
    if (project == null || project.isDisposed()) {
      presentation.setEnabledAndVisible(false);
      return;
    }
    presentation.setVisible(true);
    presentation.setEnabled(!ShelveChangesManager.getInstance(project).getDeletedLists().isEmpty());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    ShelvedChangesViewManager.getInstance(project).activateView(Collections.min(ShelveChangesManager.getInstance(project).getDeletedLists(),
                                                                                ShelvedChangesViewManager.ChangelistComparator
                                                                                  .getInstance()));
  }
}
