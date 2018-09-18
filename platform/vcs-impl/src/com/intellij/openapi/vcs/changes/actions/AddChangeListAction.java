// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.ui.NewChangelistDialog;
import org.jetbrains.annotations.NotNull;

public class AddChangeListAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    NewChangelistDialog dlg = new NewChangelistDialog(project);
    dlg.show();
    if (dlg.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
      String name = dlg.getName();
      if (name.length() == 0) {
        name = getUniqueName(project);
      }

      final LocalChangeList list = ChangeListManager.getInstance(project).addChangeList(name, dlg.getDescription());
      if (dlg.isNewChangelistActive()) {
        ChangeListManager.getInstance(project).setDefaultChangeList(list);
      }
      dlg.getPanel().changelistCreatedOrChanged(list);
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static String getUniqueName(final Project project) {
    int unnamedcount = 0;
    for (ChangeList list : ChangeListManagerImpl.getInstanceImpl(project).getChangeListsCopy()) {
      if (list.getName().startsWith("Unnamed")) {
        unnamedcount++;
      }
    }

    return unnamedcount == 0 ? "Unnamed" : "Unnamed (" + unnamedcount + ")";
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (e.getPlace().equals(ActionPlaces.CHANGES_VIEW_POPUP)) {
      ChangeList[] lists = e.getData(VcsDataKeys.CHANGE_LISTS);
      e.getPresentation().setVisible(lists != null && lists.length > 0);
    }
  }
}