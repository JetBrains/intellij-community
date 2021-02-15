// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.actions;

import com.intellij.ide.ActionsTopHitProvider;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public class VcsTopHitProvider extends ActionsTopHitProvider {
  private static final String[][] ACTION_MATRIX = {
     {"his", "history ", "Vcs.ShowTabbedFileHistory"},
     {"upd", "update ", "Vcs.UpdateProject"},
     {"pull", "pull ", "Vcs.UpdateProject"},
     {"check", "check in ", "CheckinProject"},
     {"check", "checkin ", "CheckinProject"},
     {"comm", "commit ", "CheckinProject"},
     {"check", "check in ", "ChangesView.ToggleCommitUi"},
     {"check", "checkin ", "ChangesView.ToggleCommitUi"},
     {"comm", "commit ", "ChangesView.ToggleCommitUi"},
     {"reve", "revert ", "ChangesView.Revert"},
     {"roll", "rollback ", "ChangesView.Revert"},
     {"reve", "revert ", "ChangesView.RevertFiles"},
     {"roll", "rollback ", "ChangesView.RevertFiles"},
     {"compare", "compare ", "Compare.SameVersion"},
     {"create p", "create patch ", "ChangesView.CreatePatch"},
     {"pat", "patch ", "ChangesView.CreatePatch"},
     {"pat", "patch ", "ChangesView.ApplyPatch"},
     {"appl", "apply patch ", "ChangesView.ApplyPatch"},
  };

  @Override
  protected String[] @NotNull [] getActionsMatrix() {
    return ACTION_MATRIX;
  }
}
