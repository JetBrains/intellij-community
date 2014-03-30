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

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ScrollingModel;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;

/**
 * @author max
 * @since May 14, 2002
 */
public class TextStartAction extends TextComponentEditorAction {
  public TextStartAction() {
    super(new Handler());
  }

  private static class Handler extends EditorActionHandler {
    @Override
    public void execute(Editor editor, DataContext dataContext) {
      editor.getCaretModel().removeSecondaryCarets();
      editor.getCaretModel().moveToOffset(0);
      editor.getSelectionModel().removeSelection();

      ScrollingModel scrollingModel = editor.getScrollingModel();
      scrollingModel.disableAnimation();
      scrollingModel.scrollToCaret(ScrollType.RELATIVE);
      scrollingModel.enableAnimation();

      Project project = CommonDataKeys.PROJECT.getData(dataContext);
      if (project != null) {
        IdeDocumentHistory instance = IdeDocumentHistory.getInstance(project);
        if (instance != null) {
          instance.includeCurrentCommandAsNavigation();
        }
      }
    }
  }
}
