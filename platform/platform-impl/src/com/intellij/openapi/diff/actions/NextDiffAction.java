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
package com.intellij.openapi.diff.actions;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.diff.impl.util.FocusDiffSide;
import com.intellij.openapi.editor.Editor;

public class NextDiffAction extends DiffWalkerAction {
  public static AnAction find() {
    return ActionManager.getInstance().getAction(IdeActions.ACTION_NEXT_DIFF);
  }

  protected int getLineNumberToGo(FocusDiffSide side) {
    if (side == null) return -1;
    Editor editor = side.getEditor();
    if (editor == null) return -1;
    int gotoLine = -1;
    int[] fragmentBeginnings = side.getFragmentStartingLines();
    if (fragmentBeginnings == null) return -1;
    for (int i = fragmentBeginnings.length - 1; i >= 0; i--) {
      int line = fragmentBeginnings[i];
      if (line > editor.getCaretModel().getLogicalPosition().line) {
        gotoLine = line;
      }
    }
    return gotoLine;
  }
}
