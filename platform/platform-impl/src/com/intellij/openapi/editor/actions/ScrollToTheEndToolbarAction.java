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
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * @author oleg
 */
public class ScrollToTheEndToolbarAction extends DumbAwareAction {
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
  public void update(AnActionEvent e) {
    Document document = myEditor.getDocument();
    int caretOffset = myEditor.getCaretModel().getOffset();
    Rectangle visibleArea = myEditor.getScrollingModel().getVisibleArea();
    Dimension size = myEditor.getContentComponent().getSize();
    boolean isEndVisible = visibleArea.y + visibleArea.height >= size.height;
    boolean isOnLastLine = document.getLineNumber(caretOffset) == document.getLineCount() - 1;
    e.getPresentation().setEnabled(!isEndVisible || !isOnLastLine);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    EditorUtil.scrollToTheEnd(myEditor);
  }
}
