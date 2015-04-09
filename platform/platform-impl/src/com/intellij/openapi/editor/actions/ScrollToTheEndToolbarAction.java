/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.icons.AllIcons;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

/**
 * @author oleg
 */
public class ScrollToTheEndToolbarAction extends ToggleAction implements DumbAware {
  private final Editor myEditor;

  public ScrollToTheEndToolbarAction(@NotNull final Editor editor) {
    super();
    myEditor = editor;
    final String message = ActionsBundle.message("action.EditorConsoleScrollToTheEnd.text");
    getTemplatePresentation().setDescription(message);
    getTemplatePresentation().setText(message);
    getTemplatePresentation().setIcon(AllIcons.RunConfigurations.Scroll_down);
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    Document document = myEditor.getDocument();
    return document.getLineCount() == 0 || document.getLineNumber(myEditor.getCaretModel().getOffset()) == document.getLineCount() - 1;
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    if (state) {
      EditorUtil.scrollToTheEnd(myEditor);
    } else {
      int lastLine = Math.max(0, myEditor.getDocument().getLineCount() - 1);
      LogicalPosition currentPosition = myEditor.getCaretModel().getLogicalPosition();
      LogicalPosition position = new LogicalPosition(Math.max(0, Math.min(currentPosition.line, lastLine - 1)), currentPosition.column);
      myEditor.getCaretModel().moveToLogicalPosition(position);
    }
  }
}
