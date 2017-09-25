/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import org.jetbrains.annotations.NotNull;

public class RevertSelectedChangesAction extends RevertCommittedStuffAbstractAction {
  @Override
  protected boolean isEnabled(@NotNull AnActionEvent e) {
    return super.isEnabled(e) && allSelectedChangeListsAreRevertable(e);
  }

  private static boolean allSelectedChangeListsAreRevertable(@NotNull AnActionEvent e) {
    ChangeList[] changeLists = e.getData(VcsDataKeys.CHANGE_LISTS);
    if (changeLists == null) {
      return true;
    }
    for (ChangeList list : changeLists) {
      if (list instanceof CommittedChangeList) {
        if (!((CommittedChangeList)list).isModifiable()) {
          return false;
        }
      }
    }
    return true;
  }

  public RevertSelectedChangesAction() {
    super(e -> e.getData(VcsDataKeys.SELECTED_CHANGES_IN_DETAILS), e -> {
      // to ensure directory flags for SVN are initialized
      e.getData(VcsDataKeys.CHANGES_WITH_MOVED_CHILDREN);
      return e.getData(VcsDataKeys.SELECTED_CHANGES_IN_DETAILS);
    });

    getTemplatePresentation().setText(VcsBundle.message("action.revert.selected.changes.text"));
    getTemplatePresentation().setIcon(AllIcons.Actions.Rollback);
  }
}
