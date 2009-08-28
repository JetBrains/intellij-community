package com.intellij.openapi.diff.actions;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.diff.impl.util.FocusDiffSide;
import com.intellij.openapi.editor.Editor;

public class PreviousDiffAction extends DiffWalkerAction {
  public static AnAction find() {
    return ActionManager.getInstance().getAction(IdeActions.ACTION_PREVIOUS_DIFF);
  }

  protected int getLineNumberToGo(FocusDiffSide side) {
    if (side == null) return -1;
    Editor editor = side.getEditor();
    if (editor == null) return -1;
    int[] fragmentBeginnings = side.getFragmentStartingLines();
    int gotoLine = -1;
    if (fragmentBeginnings == null) return -1;
    for (int i = 0; i < fragmentBeginnings.length; i++) {
      int line = fragmentBeginnings[i];
      if (line < editor.getCaretModel().getLogicalPosition().line) {
        gotoLine = line;
      }
    }
    return gotoLine;
  }
}
