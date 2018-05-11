// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.ui.EditChangelistDialog;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nullable;

public class RenameChangeListAction extends AnAction implements DumbAware {

  public void update(AnActionEvent e) {
    LocalChangeList target = getTargetChangeList(e);
    final boolean visible = target != null && !target.isReadOnly();
    e.getPresentation().setEnabled(visible);
    if (e.getPlace().equals(ActionPlaces.CHANGES_VIEW_POPUP)) {
      e.getPresentation().setVisible(visible);
    }
  }

  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    LocalChangeList target = getTargetChangeList(e);
    if (target != null) {
      new EditChangelistDialog(project, target).show();
    }
  }

  @Nullable
  private static LocalChangeList getTargetChangeList(AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return null;
    ChangeList[] lists = e.getData(VcsDataKeys.CHANGE_LISTS);
    if (!ArrayUtil.isEmpty(lists)) {
      if (lists.length == 1) {
        return ChangeListManager.getInstance(project).findChangeList(lists[0].getName());
      }
      return null;
    }
    Change[] changes = e.getData(VcsDataKeys.CHANGES);
    if (changes == null) return null;

    LocalChangeList result = null;
    for (Change change : changes) {
      LocalChangeList cl = ChangeListManager.getInstance(project).getChangeList(change);
      if (result == null)
        result = cl;
      else if (cl != null && !cl.equals(result))
        return null;
    }
    return result;
  }
}