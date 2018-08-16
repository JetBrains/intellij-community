// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.actions;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class RemoveChangeListAction extends AnAction implements DumbAware {
  @Override
  public void update(@NotNull AnActionEvent e) {
    ChangeList[] changeListsArray = e.getData(VcsDataKeys.CHANGE_LISTS);
    List<ChangeList> changeLists = changeListsArray != null ? Arrays.asList(changeListsArray) : Collections.emptyList();

    boolean hasChanges = !ArrayUtil.isEmpty(e.getData(VcsDataKeys.CHANGES));
    boolean enabled = canRemoveChangeLists(e.getProject(), changeLists);

    Presentation presentation = e.getPresentation();
    presentation.setEnabled(enabled);
    if (e.getPlace().equals(ActionPlaces.CHANGES_VIEW_POPUP)) {
      presentation.setVisible(enabled);
    }

    presentation.setText(ActionsBundle.message("action.ChangesView.RemoveChangeList.text.template", changeLists.size()));
    if (hasChanges) {
      boolean containsActiveChangelist = ContainerUtil.exists(changeLists, l -> l instanceof LocalChangeList && ((LocalChangeList)l).isDefault());
      presentation.setDescription(ActionsBundle.message("action.ChangesView.RemoveChangeList.description.template",
                                                        changeLists.size(), containsActiveChangelist ? "another" : "default"));
    }
    else {
      presentation.setDescription(null);
    }
  }

  private static boolean canRemoveChangeLists(@Nullable Project project, @NotNull List<ChangeList> lists) {
    if (project == null || lists.size() == 0) return false;

    int allChangeListsCount = ChangeListManager.getInstance(project).getChangeListsNumber();
    for(ChangeList changeList: lists) {
      if (!(changeList instanceof LocalChangeList)) return false;
      LocalChangeList localChangeList = (LocalChangeList) changeList;
      if (localChangeList.isReadOnly()) return false;
      if (localChangeList.isDefault() && allChangeListsCount <= lists.size()) return false;
    }
    return true;
  }

  @Override
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
    boolean activeChangelistSelected = lists.stream().anyMatch(LocalChangeList::isDefault);
    boolean haveNoChanges = lists.stream().allMatch(l -> l.getChanges().isEmpty());

    if (activeChangelistSelected) {
      return confirmActiveChangeListRemoval(project, lists, haveNoChanges);
    }

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

    // Can't remove last changelist
    if (remainingLists.isEmpty()) {
      return false;
    }

    // don't ask "Which changelist to make active" if there is only one option anyway
    // unless there are some changes to be moved - give user a chance to cancel deletion
    if (remainingLists.size() == 1 && empty) {
      ChangeListManager.getInstance(project).setDefaultChangeList(remainingLists.get(0));
      return true;
    }

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