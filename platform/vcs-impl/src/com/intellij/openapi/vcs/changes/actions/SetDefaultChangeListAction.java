// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import org.jetbrains.annotations.NotNull;

public class SetDefaultChangeListAction extends AnAction implements DumbAware {
  @Override
  public void update(@NotNull AnActionEvent e) {
    ChangeList[] lists = e.getData(VcsDataKeys.CHANGE_LISTS);
    final boolean visible =
      lists != null && lists.length == 1 && lists[0] instanceof LocalChangeList && !((LocalChangeList)lists[0]).isDefault();
    e.getPresentation().setEnabled(visible);
    if (e.getPlace().equals(ActionPlaces.CHANGES_VIEW_POPUP)) {
      e.getPresentation().setVisible(visible);
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    final ChangeList[] lists = e.getData(VcsDataKeys.CHANGE_LISTS);
    assert lists != null;
    ChangeListManager.getInstance(project).setDefaultChangeList((LocalChangeList)lists[0]);
  }
}
