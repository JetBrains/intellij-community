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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: May 14, 2002
 * Time: 6:29:03 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.textarea.TextComponentEditor;
import com.intellij.openapi.keymap.impl.ModifierKeyDoubleClickHandler;
import org.jetbrains.annotations.NotNull;

public class LineEndWithSelectionAction extends TextComponentEditorAction {
  public LineEndWithSelectionAction() {
    super(new Handler());
  }

  private static class Handler extends EditorActionHandler {
    public Handler() {
      super(true);
    }

    @Override
    protected boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
      return !ModifierKeyDoubleClickHandler.getInstance().isRunningAction() ||
             EditorSettingsExternalizable.getInstance().addCaretsOnDoubleCtrl();
    }

    @Override
    protected void doExecute(Editor editor, Caret caret, DataContext dataContext) {
      EditorActionUtil.moveCaretToLineEnd(editor, true, !(editor instanceof TextComponentEditor));
    }
  }
}
