/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.execution.impl.ConsoleViewUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Denis Zhdanov
 */
public class ResetFontSizeAction extends EditorAction {

  public ResetFontSizeAction() {
    super(new MyHandler());
  }
  
  private static class MyHandler extends EditorActionHandler {
    @Override
    public void doExecute(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
      if (!(editor instanceof EditorEx)) {
        return;
      }
      EditorColorsScheme globalScheme = EditorColorsManager.getInstance().getGlobalScheme();
      int fontSize = ConsoleViewUtil.isConsoleViewEditor(editor)
                     ? globalScheme.getConsoleFontSize() : globalScheme.getEditorFontSize();
      EditorEx editorEx = (EditorEx)editor;
      editorEx.setFontSize(fontSize);
    }
  }
}
