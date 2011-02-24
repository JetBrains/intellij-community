/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.ex.EditorEx;

/**
 * Scrolls to the top of the target editor without changing its caret position.
 * 
 * @author Denis Zhdanov
 * @since 2/22/11 11:17 AM
 */
public class ScrollToBottomAction extends InactiveEditorAction {

  public ScrollToBottomAction() {
    super(new MyHandler());
  }

  private static class MyHandler extends EditorActionHandler {
    @Override
    public void execute(Editor editor, DataContext dataContext) {
      if (editor == null) {
        return;
      }
      editor.getScrollingModel().scrollTo(editor.offsetToLogicalPosition(editor.getDocument().getTextLength()), ScrollType.CENTER);
    }
  }
}
