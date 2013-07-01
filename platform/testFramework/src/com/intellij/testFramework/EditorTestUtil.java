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
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.psi.tree.IElementType;
import junit.framework.Assert;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * User: Maxim.Mossienko
 * Date: 15.03.2010
 * Time: 21:00:49
 */
public class EditorTestUtil {
  public static final String CARET_TAG = "<caret>";
  public static final String CARET_TAG_PREFIX = CARET_TAG.substring(0, CARET_TAG.length() - 1);

  public static final String SELECTION_START_TAG = "<selection>";
  public static final String SELECTION_END_TAG = "</selection>";

  public static final char BACKSPACE_FAKE_CHAR = '\uFFFF';
  public static final char SMART_ENTER_FAKE_CHAR = '\uFFFE';
  public static final char SMART_LINE_SPLIT_CHAR = '\uFFFD';

  public static void performTypingAction(Editor editor, char c) {
    EditorActionManager actionManager = EditorActionManager.getInstance();
    if (c == BACKSPACE_FAKE_CHAR) {
      EditorActionHandler actionHandler = actionManager.getActionHandler(IdeActions.ACTION_EDITOR_BACKSPACE);
      actionHandler.execute(editor, DataManager.getInstance().getDataContext());
    } else if (c == SMART_ENTER_FAKE_CHAR) {
      EditorActionHandler actionHandler = actionManager.getActionHandler(IdeActions.ACTION_EDITOR_COMPLETE_STATEMENT);
      actionHandler.execute(editor, DataManager.getInstance().getDataContext());
    } else if (c == SMART_LINE_SPLIT_CHAR) {
      EditorActionHandler actionHandler = actionManager.getActionHandler(IdeActions.ACTION_EDITOR_SPLIT);
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

  public static void performReferenceCopy(DataContext dataContext) {
    ActionManager actionManager = ActionManager.getInstance();
    AnAction action = actionManager.getAction(IdeActions.ACTION_COPY_REFERENCE);
    AnActionEvent
      event = new AnActionEvent(null, dataContext, "", action.getTemplatePresentation(),
                                            ActionManager.getInstance(), 0);
    action.update(event);
    Assert.assertTrue(event.getPresentation().isEnabled());
    action.actionPerformed(event);
  }

  public static void performPaste(Editor editor) {
    EditorActionManager actionManager = EditorActionManager.getInstance();
    EditorActionHandler actionHandler = actionManager.getActionHandler(IdeActions.ACTION_EDITOR_PASTE);
    actionHandler.execute(editor, DataManager.getInstance().getDataContext());
  }

  public static List<IElementType> getAllTokens(EditorHighlighter highlighter) {
    List<IElementType> tokens = new ArrayList<IElementType>();
    HighlighterIterator iterator = highlighter.createIterator(0);
    while (!iterator.atEnd()) {
      tokens.add(iterator.getTokenType());
      iterator.advance();
    }
    return tokens;
  }

  public static int getCaretPosition(@NotNull final String content) {
    return getCaretAndSelectionPosition(content)[0];
  }

  public static int[] getCaretAndSelectionPosition(@NotNull final String content) {
    int caretPosInSourceFile = content.indexOf(CARET_TAG_PREFIX);
    int caretEndInSourceFile = content.indexOf(">", caretPosInSourceFile);
    int caretLength = caretEndInSourceFile - caretPosInSourceFile;
    int visualColumnOffset = 0;
    if (caretPosInSourceFile >= 0) {
      String visualOffsetString = content.substring(caretPosInSourceFile + CARET_TAG_PREFIX.length(), caretEndInSourceFile);
      if (visualOffsetString.length() > 1) {
        visualColumnOffset = Integer.parseInt(visualOffsetString.substring(1));
      }
    }
    int selectionStartInSourceFile = content.indexOf(SELECTION_START_TAG);
    int selectionEndInSourceFile = content.indexOf(SELECTION_END_TAG);
    if (selectionStartInSourceFile >= 0) {
      if (caretPosInSourceFile >= 0) {
        if (caretPosInSourceFile < selectionStartInSourceFile) {
          selectionStartInSourceFile -= caretLength;
          selectionEndInSourceFile -= caretLength;
        }
        else {
          if (caretPosInSourceFile < selectionEndInSourceFile) {
            caretPosInSourceFile -= SELECTION_START_TAG.length();
          }
          else {
            caretPosInSourceFile -= SELECTION_START_TAG.length() + SELECTION_END_TAG.length();
          }
        }
      }
      selectionEndInSourceFile -= SELECTION_START_TAG.length();
    }

    return new int[]{caretPosInSourceFile, visualColumnOffset, selectionStartInSourceFile, selectionEndInSourceFile};
  }
}
