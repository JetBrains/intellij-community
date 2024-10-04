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

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesTreeBrowser;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public abstract class RevertSelectedChangesAction extends RevertCommittedStuffAbstractAction {
  public static class Revert extends RevertSelectedChangesAction {
    public Revert() {
      super(true);
    }
  }

  public static class Apply extends RevertSelectedChangesAction {
    public Apply() {
      super(false);
    }
  }

  protected RevertSelectedChangesAction(boolean reverse) {
    super(reverse);
  }

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

  @Override
  protected Change @Nullable [] getChanges(@NotNull AnActionEvent e, boolean isFromUpdate) {
    CommittedChangesTreeBrowser treeBrowser = e.getData(CommittedChangesTreeBrowser.COMMITTED_CHANGES_TREE_DATA_KEY);
    if (treeBrowser == null) {
      return e.getData(VcsDataKeys.SELECTED_CHANGES_IN_DETAILS);
    }

    if (isFromUpdate) {
      return e.getData(VcsDataKeys.SELECTED_CHANGES_IN_DETAILS);
    }
    else {
      return treeBrowser.collectSelectedChangesWithMovedChildren();
    }
  }
}
