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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.diff.impl.DiffUtil;
import com.intellij.openapi.diff.impl.util.FocusDiffSide;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.DumbAware;

import java.awt.event.KeyEvent;

public abstract class DiffWalkerAction extends AnAction implements DumbAware {
  protected DiffWalkerAction() {
    setEnabledInModalContext(true);
  }

  public void actionPerformed(AnActionEvent event) {
    FocusDiffSide side = DiffUtil.getFocusDiffSide(event.getDataContext());
    if (side == null) return;
    int line = getLineNumberToGo(side);
    Editor editor = side.getEditor();
    if (line >= 0 && editor != null) {
      LogicalPosition pos = new LogicalPosition(line, 0);
      editor.getCaretModel().removeSecondaryCarets();
      editor.getCaretModel().moveToLogicalPosition(pos);
      editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
    }
  }

  public void update(AnActionEvent event) {
    FocusDiffSide side = DiffUtil.getFocusDiffSide(event.getDataContext());
    Presentation presentation = event.getPresentation();
    if (side == null) {
      presentation.setEnabled(false);
    } else {
      presentation.setEnabled(getLineNumberToGo(side) >= 0 || event.getInputEvent() instanceof KeyEvent);
    }
  }

  protected abstract int getLineNumberToGo(FocusDiffSide side);
}
