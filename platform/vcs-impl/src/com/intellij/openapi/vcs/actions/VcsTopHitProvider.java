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
package com.intellij.openapi.vcs.actions;

import com.intellij.ide.ActionsTopHitProvider;

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

  @Override
  protected String[][] getActionsMatrix() {
    return ACTION_MATRIX;
  }
}
