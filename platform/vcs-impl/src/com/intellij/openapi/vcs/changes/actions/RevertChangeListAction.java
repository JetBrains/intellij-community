// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class RevertChangeListAction extends RevertCommittedStuffAbstractAction {
  public RevertChangeListAction() {
    super(true);
  }

  @Override
  protected Change @Nullable [] getChanges(@NotNull AnActionEvent e, boolean isFromUpdate) {
    if (isFromUpdate) {
      return e.getData(VcsDataKeys.CHANGES);
    }
    else {
      return e.getData(VcsDataKeys.CHANGES_WITH_MOVED_CHILDREN);
    }
  }
}
