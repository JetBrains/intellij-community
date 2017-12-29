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

package com.intellij.openapi.editor.actions;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;

public class UnindentSelectionAction extends EditorAction {
  public UnindentSelectionAction() {
    super(new Handler());
  }

  private static class Handler extends EditorWriteActionHandler {
    public Handler() {
      super(true);
    }

    @Override
    public void executeWriteAction(Editor editor, DataContext dataContext) {
      Project project = CommonDataKeys.PROJECT.getData(dataContext);
      unindentSelection(editor, project);
    }

    @Override
    public boolean isEnabled(Editor editor, DataContext dataContext) {
      return !editor.isViewer() && !editor.isOneLineMode() && !((EditorEx)editor).isEmbeddedIntoDialogWrapper();
    }
  }

  private static void unindentSelection(Editor editor, Project project) {
    int oldSelectionStart = editor.getSelectionModel().getSelectionStart();
    int oldSelectionEnd = editor.getSelectionModel().getSelectionEnd();
    if(!editor.getSelectionModel().hasSelection()) {
      oldSelectionStart = editor.getCaretModel().getOffset();
      oldSelectionEnd = oldSelectionStart;
    }

    Document document = editor.getDocument();
    int startIndex = document.getLineNumber(oldSelectionStart);
    if(startIndex == -1) {
      startIndex = document.getLineCount() - 1;
    }
    int endIndex = document.getLineNumber(oldSelectionEnd);
    if(endIndex > 0 && document.getLineStartOffset(endIndex) == oldSelectionEnd && endIndex > startIndex) {
      endIndex --;
    }
    if(endIndex == -1) {
      endIndex = document.getLineCount() - 1;
    }

    if (startIndex < 0 || endIndex < 0) return;

    int blockIndent = CodeStyle.getIndentOptions(project, document).INDENT_SIZE;
    IndentSelectionAction.doIndent(endIndex, startIndex, document, project, editor, -blockIndent);
  }
}
