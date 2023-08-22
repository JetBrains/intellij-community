// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.EditorLastActionTracker;
import com.intellij.openapi.keymap.impl.ModifierKeyDoubleClickHandler;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class CloneCaretActionHandler extends EditorActionHandler {
  private static final Key<Integer> LEVEL = Key.create("CloneCaretActionHandler.level");

  private static final Set<String> OUR_ACTIONS = Set.of(
    IdeActions.ACTION_EDITOR_CLONE_CARET_ABOVE,
    IdeActions.ACTION_EDITOR_CLONE_CARET_BELOW,
    IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT_WITH_SELECTION,
    IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT_WITH_SELECTION,
    IdeActions.ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION,
    IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION,
    IdeActions.ACTION_EDITOR_MOVE_LINE_START_WITH_SELECTION,
    IdeActions.ACTION_EDITOR_MOVE_LINE_END_WITH_SELECTION,
    IdeActions.ACTION_EDITOR_MOVE_CARET_PAGE_UP_WITH_SELECTION,
    IdeActions.ACTION_EDITOR_MOVE_CARET_PAGE_DOWN_WITH_SELECTION,
    IdeActions.ACTION_EDITOR_PREVIOUS_WORD_WITH_SELECTION,
    IdeActions.ACTION_EDITOR_NEXT_WORD_WITH_SELECTION
  );

  private final boolean myCloneAbove;

  private boolean myRepeatedInvocation;

  public CloneCaretActionHandler(boolean above) {
    myCloneAbove = above;
  }

  @Override
  public boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
    return editor.getCaretModel().supportsMultipleCarets() && (!ModifierKeyDoubleClickHandler.getInstance().isRunningAction() ||
                                                               EditorSettingsExternalizable.getInstance().addCaretsOnDoubleCtrl());
  }

  @Override
  protected void doExecute(@NotNull Editor editor, @Nullable Caret targetCaret, DataContext dataContext) {
    if (targetCaret != null) {
      if (!EditorUtil.checkMaxCarets(editor)) {
        targetCaret.clone(myCloneAbove);
      }
      return;
    }
    int currentLevel = 0;
    List<Caret> currentCarets = new ArrayList<>();
    for (Caret caret : editor.getCaretModel().getAllCarets()) {
      int level = getLevel(caret);
      if (Math.abs(level) > Math.abs(currentLevel)) {
        currentLevel = level;
        currentCarets.clear();
      }
      if (Math.abs(level) == Math.abs(currentLevel)) {
        currentCarets.add(caret);
      }
    }
    boolean removeCarets = currentLevel > 0 && myCloneAbove || currentLevel < 0 && !myCloneAbove;
    Integer newLevel = myCloneAbove ? currentLevel - 1 : currentLevel + 1;
    for (Caret caret : currentCarets) {
      if (removeCarets) {
        editor.getCaretModel().removeCaret(caret);
      }
      else {
        Caret clone = caret;
        do {
          Caret original = clone;
          clone = clone.clone(myCloneAbove);
          if (original != caret) {
            editor.getCaretModel().removeCaret(original);
          }
        } while (clone != null && caret.hasSelection() && !clone.hasSelection());
        if (clone == null) {
          if (EditorUtil.checkMaxCarets(editor)) break;
        }
        else {
          clone.putUserData(LEVEL, newLevel);
        }
      }
    }
    if (removeCarets) {
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    }
  }

  public void setRepeatedInvocation(boolean value) {
    myRepeatedInvocation = value;
  }

  private int getLevel(Caret caret) {
    if (isRepeatedActionInvocation()) {
      Integer value = caret.getUserData(LEVEL);
      return value == null ? 0 : value;
    }
    else {
      caret.putUserData(LEVEL, null);
      return 0;
    }
  }

  private boolean isRepeatedActionInvocation() {
    if (myRepeatedInvocation) return true;
    String lastActionId = EditorLastActionTracker.getInstance().getLastActionId();
    return lastActionId != null && isSuitableLastAction(lastActionId);
  }

  protected boolean isSuitableLastAction(@NotNull String lastActionId){
    return OUR_ACTIONS.contains(lastActionId);
  }
}
