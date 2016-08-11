/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.editor.actions;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorLastActionTracker;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.keymap.impl.ModifierKeyDoubleClickHandler;
import com.intellij.openapi.util.Key;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class CloneCaretActionHandler extends EditorActionHandler {
  private static final Key<Integer> LEVEL = Key.create("CloneCaretActionHandler.level");

  private static final Set<String> OUR_ACTIONS = new HashSet<>(Arrays.asList(
    IdeActions.ACTION_EDITOR_CLONE_CARET_ABOVE,
    IdeActions.ACTION_EDITOR_CLONE_CARET_BELOW,
    IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT_WITH_SELECTION,
    IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT_WITH_SELECTION,
    IdeActions.ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION,
    IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION,
    IdeActions.ACTION_EDITOR_MOVE_LINE_START_WITH_SELECTION,
    IdeActions.ACTION_EDITOR_MOVE_LINE_END_WITH_SELECTION
  ));

  private final boolean myCloneAbove;

  public CloneCaretActionHandler(boolean above) {
    myCloneAbove = above;
  }

  @Override
  public boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
    return editor.getCaretModel().supportsMultipleCarets() && (!ModifierKeyDoubleClickHandler.getInstance().isRunningAction() ||
                                                               EditorSettingsExternalizable.getInstance().addCaretsOnDoubleCtrl());
  }

  @Override
  protected void doExecute(Editor editor, @Nullable Caret targetCaret, DataContext dataContext) {
    if (ModifierKeyDoubleClickHandler.getInstance().isRunningAction() && !isRepeatedActionInvocation()) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.add.carets.using.double.ctrl");
    }
    if (targetCaret != null) {
      targetCaret.clone(myCloneAbove);
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
        if (clone != null) {
          clone.putUserData(LEVEL, newLevel);
        }
      }
    }
    if (removeCarets) {
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    }
  }

  private static int getLevel(Caret caret) {
    if (isRepeatedActionInvocation()) {
      Integer value = caret.getUserData(LEVEL);
      return value == null ? 0 : value;
    }
    else {
      caret.putUserData(LEVEL, null);
      return 0;
    }
  }

  private static boolean isRepeatedActionInvocation() {
    String lastActionId = EditorLastActionTracker.getInstance().getLastActionId();
    return OUR_ACTIONS.contains(lastActionId);
  }
}
