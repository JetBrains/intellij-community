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

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 02.11.2006
 * Time: 21:57:44
 */
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class RemoveChangeListAction extends AnAction implements DumbAware {
  public void update(AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    ChangeList[] lists = e.getData(VcsDataKeys.CHANGE_LISTS);
    final boolean visible = canRemoveChangeLists(project, lists);
    if (e.getPlace().equals(ActionPlaces.CHANGES_VIEW_POPUP))
      e.getPresentation().setVisible(visible);
    else
      e.getPresentation().setEnabled(visible);
  }

  private static boolean canRemoveChangeLists(final Project project, final ChangeList[] lists) {
    if (project == null || lists == null || lists.length == 0) return false;
    for(ChangeList changeList: lists) {
      if (!(changeList instanceof LocalChangeList)) return false;
      LocalChangeList localChangeList = (LocalChangeList) changeList;
      if (localChangeList.isReadOnly()) return false;
      if (localChangeList.isDefault() && ChangeListManager.getInstance(project).getChangeListsCopy().size() <= lists.length) return false;
    }
    return true;
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    final ChangeList[] lists = e.getData(VcsDataKeys.CHANGE_LISTS);
    assert lists != null;
    if (! canRemoveChangeLists(project, lists)) {
      return;
    }

    //noinspection unchecked
    ChangeListRemoveConfirmation.processLists(project, true, (Collection)Arrays.asList(lists), new ChangeListRemoveConfirmation() {
      @Override
      public boolean askIfShouldRemoveChangeLists(@NotNull List<? extends LocalChangeList> lists1) {
        return RemoveChangeListAction.askIfShouldRemoveChangeLists(lists1, project);
      }
    });
  }

  private static boolean askIfShouldRemoveChangeLists(@NotNull List<? extends LocalChangeList> lists, Project project) {
    for (LocalChangeList list : lists) {
      if (list.isDefault()) {
        return confirmActiveChangeListRemoval(project, lists, list.getChanges().isEmpty());
      }
    }

    int rc;
    if (lists.size() == 1) {
      final LocalChangeList list = lists.get(0);
      rc = list.getChanges().size() == 0 ? Messages.YES :
               Messages.showYesNoDialog(project,
                                        VcsBundle.message("changes.removechangelist.warning.text", list.getName()),
                                        VcsBundle.message("changes.removechangelist.warning.title"),
                                        Messages.getQuestionIcon());
    }
    else {
      boolean notEmpty = false;
      for (ChangeList list : lists) {
        notEmpty |= (list.getChanges().size() > 0);
      }
      rc = (! notEmpty) ? Messages.YES : Messages.showYesNoDialog(project,
                                    VcsBundle.message("changes.removechangelist.multiple.warning.text", lists.size()),
                                    VcsBundle.message("changes.removechangelist.warning.title"),
                                    Messages.getQuestionIcon());
      
    }
    return rc == Messages.YES;
  }

  static boolean confirmActiveChangeListRemoval(final Project project, final List<? extends LocalChangeList> lists, final boolean empty) {
    List<LocalChangeList> remainingLists = ChangeListManager.getInstance(project).getChangeListsCopy();
    remainingLists.removeAll(lists);
    String[] names = new String[remainingLists.size()];
    for(int i=0; i<remainingLists.size(); i++) {
      names [i] = remainingLists.get(i).getName();
    }
    int nameIndex = Messages.showChooseDialog(project,
                                              empty ? VcsBundle.message("changes.remove.active.empty.prompt") : VcsBundle.message("changes.remove.active.prompt"),
                                              VcsBundle.message("changes.remove.active.title"),
                                              Messages.getQuestionIcon(), names, names [0]);
    if (nameIndex < 0) return false;
    ChangeListManager.getInstance(project).setDefaultChangeList(remainingLists.get(nameIndex));
    return true;
  }
}