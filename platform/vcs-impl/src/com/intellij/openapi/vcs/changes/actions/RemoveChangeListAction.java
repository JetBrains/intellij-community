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
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class RemoveChangeListAction extends AnAction implements DumbAware {
  public void update(@NotNull AnActionEvent e) {
    boolean visible = canRemoveChangeLists(e.getProject(), e.getData(VcsDataKeys.CHANGE_LISTS));

    e.getPresentation().setEnabled(visible);
    if (e.getPlace().equals(ActionPlaces.CHANGES_VIEW_POPUP)) {
      e.getPresentation().setVisible(visible);
    }
  }

  private static boolean canRemoveChangeLists(@Nullable Project project, @Nullable ChangeList[] lists) {
    if (project == null || lists == null || lists.length == 0) return false;

    int allChangeListsCount = ChangeListManager.getInstance(project).getChangeListsNumber();
    for(ChangeList changeList: lists) {
      if (!(changeList instanceof LocalChangeList)) return false;
      LocalChangeList localChangeList = (LocalChangeList) changeList;
      if (localChangeList.isReadOnly()) return false;
      if (localChangeList.isDefault() && allChangeListsCount <= lists.length) return false;
    }
    return true;
  }

  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    final ChangeList[] selectedLists = e.getRequiredData(VcsDataKeys.CHANGE_LISTS);

    //noinspection unchecked
    ChangeListRemoveConfirmation.processLists(project, true, (Collection)Arrays.asList(selectedLists), new ChangeListRemoveConfirmation() {
      @Override
      public boolean askIfShouldRemoveChangeLists(@NotNull List<? extends LocalChangeList> lists) {
        return RemoveChangeListAction.askIfShouldRemoveChangeLists(lists, project);
      }
    });
  }

  private static boolean askIfShouldRemoveChangeLists(@NotNull List<? extends LocalChangeList> lists, Project project) {
    for (LocalChangeList list : lists) {
      if (list.isDefault()) {
        return confirmActiveChangeListRemoval(project, lists, list.getChanges().isEmpty());
      }
    }

    boolean haveNoChanges = lists.stream().noneMatch(list -> !list.getChanges().isEmpty());
    String message = lists.size() == 1
                     ? VcsBundle.message("changes.removechangelist.warning.text", lists.get(0).getName())
                     : VcsBundle.message("changes.removechangelist.multiple.warning.text", lists.size());

    return haveNoChanges ||
           Messages.YES ==
           Messages
             .showYesNoDialog(project, message, VcsBundle.message("changes.removechangelist.warning.title"), Messages.getQuestionIcon());
  }

  static boolean confirmActiveChangeListRemoval(@NotNull Project project, @NotNull List<? extends LocalChangeList> lists, boolean empty) {
    List<LocalChangeList> remainingLists = ChangeListManager.getInstance(project).getChangeListsCopy();
    remainingLists.removeAll(lists);

    String[] remainingListsNames = remainingLists.stream().map(ChangeList::getName).toArray(String[]::new);
    int nameIndex = Messages.showChooseDialog(project, empty
                                                       ? VcsBundle.message("changes.remove.active.empty.prompt")
                                                       : VcsBundle.message("changes.remove.active.prompt"),
                                              VcsBundle.message("changes.remove.active.title"), Messages.getQuestionIcon(),
                                              remainingListsNames, remainingListsNames[0]);
    if (nameIndex < 0) return false;
    ChangeListManager.getInstance(project).setDefaultChangeList(remainingLists.get(nameIndex));
    return true;
  }
}