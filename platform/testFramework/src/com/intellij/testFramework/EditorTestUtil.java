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
package com.intellij.testFramework;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.TypedAction;

/**
 * User: Maxim.Mossienko
 * Date: 15.03.2010
 * Time: 21:00:49
 */
public class EditorTestUtil {
  public static final char BACKSPACE_FAKE_CHAR = '\uFFFF';
  public static final char SMART_ENTER_FAKE_CHAR = '\uFFFE';

  public static void performTypingAction(Editor editor, char c) {
    EditorActionManager actionManager = EditorActionManager.getInstance();
    if (c == BACKSPACE_FAKE_CHAR) {
      EditorActionHandler actionHandler = actionManager.getActionHandler(IdeActions.ACTION_EDITOR_BACKSPACE);
      actionHandler.execute(editor, DataManager.getInstance().getDataContext());
    } else if (c == SMART_ENTER_FAKE_CHAR) {
      EditorActionHandler actionHandler = actionManager.getActionHandler(IdeActions.ACTION_EDITOR_COMPLETE_STATEMENT);
      actionHandler.execute(editor, DataManager.getInstance().getDataContext());
    }
    else if (c == '\n') {
      EditorActionHandler actionHandler = actionManager.getActionHandler(IdeActions.ACTION_EDITOR_ENTER);
      actionHandler.execute(editor, DataManager.getInstance().getDataContext());
    }
    else {
      TypedAction action = actionManager.getTypedAction();
      action.actionPerformed(editor, c, DataManager.getInstance().getDataContext());
    }
  }
}
