/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.AnActionExtensionProvider;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.diff.impl.DiffUtil;
import com.intellij.openapi.diff.impl.util.FocusDiffSide;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import org.jetbrains.annotations.NotNull;

import java.awt.event.KeyEvent;

public abstract class DiffWalkerAction implements AnActionExtensionProvider {
  @Override
  public boolean isActive(@NotNull AnActionEvent event) {
    return DiffUtil.getFocusDiffSide(event.getDataContext()) != null;
  }

  public void actionPerformed(@NotNull AnActionEvent event) {
    FocusDiffSide side = DiffUtil.getFocusDiffSide(event.getDataContext());
    if (side == null) return;
    Editor editor = side.getEditor();
    if (editor == null) return;

    int line = getLineNumberToGo(side, editor);
    if (line >= 0) {
      LogicalPosition pos = new LogicalPosition(line, 0);
      editor.getCaretModel().removeSecondaryCarets();
      editor.getCaretModel().moveToLogicalPosition(pos);
      editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
    }
  }

  public void update(@NotNull AnActionEvent event) {
    FocusDiffSide side = DiffUtil.getFocusDiffSide(event.getDataContext());
    Presentation presentation = event.getPresentation();
    if (side == null) {
      presentation.setEnabled(false);
    } else {
      Editor editor = side.getEditor();
      presentation.setEnabled(editor != null && getLineNumberToGo(side, editor) >= 0 ||
                              event.getInputEvent() instanceof KeyEvent);
    }
  }

  private int getLineNumberToGo(@NotNull FocusDiffSide side, @NotNull Editor editor) {
    int[] fragmentBeginnings = side.getFragmentStartingLines();
    if (fragmentBeginnings == null) return -1;
    int caretLine = editor.getCaretModel().getLogicalPosition().line;
    return getLineNumberToGo(fragmentBeginnings, caretLine);
  }

  protected abstract int getLineNumberToGo(@NotNull int[] fragmentBeginnings, int line);


  public static class Previous extends DiffWalkerAction {
    @Override
    protected int getLineNumberToGo(@NotNull int[] fragmentBeginnings, int caretLine) {
      int gotoLine = -1;
      for (int i = 0; i < fragmentBeginnings.length; i++) {
        int line = fragmentBeginnings[i];
        if (line < caretLine) {
          gotoLine = line;
        }
      }
      return gotoLine;
    }
  }

  public static class Next extends DiffWalkerAction {
    @Override
    protected int getLineNumberToGo(@NotNull int[] fragmentBeginnings, int caretLine) {
      int gotoLine = -1;
      for (int i = fragmentBeginnings.length - 1; i >= 0; i--) {
        int line = fragmentBeginnings[i];
        if (line > caretLine) {
          gotoLine = line;
        }
      }
      return gotoLine;
    }
  }
}
