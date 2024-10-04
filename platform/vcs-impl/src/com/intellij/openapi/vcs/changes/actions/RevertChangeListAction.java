// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesTreeBrowser;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class RevertChangeListAction extends RevertCommittedStuffAbstractAction {
  public RevertChangeListAction() {
    super(true);
  }

  @Override
  protected Change @Nullable [] getChanges(@NotNull AnActionEvent e, boolean isFromUpdate) {
    CommittedChangesTreeBrowser treeBrowser = e.getData(CommittedChangesTreeBrowser.COMMITTED_CHANGES_TREE_DATA_KEY);
    if (treeBrowser == null) {
      return null;
    }

    if (isFromUpdate) {
      return e.getData(VcsDataKeys.CHANGES);
    }
    else {
      return treeBrowser.collectChangesWithMovedChildren();
    }
  }
}
