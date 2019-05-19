// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
     {"reve", "revert ", "ChangesView.Revert"},
     {"roll", "rollback ", "ChangesView.Revert"},
     {"compare", "compare ", "Compare.SameVersion"},
     {"create p", "create patch ", "ChangesView.CreatePatch"},
     {"pat", "patch ", "ChangesView.CreatePatch"},
     {"pat", "patch ", "ChangesView.ApplyPatch"},
     {"appl", "apply patch ", "ChangesView.ApplyPatch"},
  };

  @NotNull
  @Override
  protected String[][] getActionsMatrix() {
    return ACTION_MATRIX;
  }
}
