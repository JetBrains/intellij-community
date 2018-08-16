// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.EditSourceAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserBase;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.stream.Stream;

import static com.intellij.openapi.vcs.changes.ChangesUtil.getFiles;
import static com.intellij.openapi.vcs.changes.ChangesUtil.getNavigatableArray;

class EditSourceFromChangesBrowserAction extends EditSourceAction {
  private final Icon myEditSourceIcon;

  public EditSourceFromChangesBrowserAction() {
    myEditSourceIcon = AllIcons.Actions.EditSource;
  }

  @Override
  public void update(@NotNull final AnActionEvent event) {
    super.update(event);
    event.getPresentation().setIcon(myEditSourceIcon);
    event.getPresentation().setText("Edit Source");
    if (event.getData(ChangesBrowserBase.DATA_KEY) == null) {
      event.getPresentation().setEnabledAndVisible(false);
    }
    else if ((!ModalityState.NON_MODAL.equals(ModalityState.current())) ||
             CommittedChangesBrowserUseCase.IN_AIR.equals(CommittedChangesBrowserUseCase.DATA_KEY.getData(event.getDataContext()))) {
      event.getPresentation().setEnabled(false);
    }
  }

  @Override
  protected Navigatable[] getNavigatables(DataContext dataContext) {
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    Change[] changes = VcsDataKeys.SELECTED_CHANGES.getData(dataContext);
    return changes != null && project != null ? getNavigatableArray(project, getFiles(Stream.of(changes))) : null;
  }
}
