/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.EmptyClipboardOwner;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;

public class CutLineEndAction extends EditorAction {
  public CutLineEndAction() {
    super(new Handler());
  }

  private static class Handler extends EditorWriteActionHandler {
    public void executeWriteAction(Editor editor, DataContext dataContext) {
      final Document doc = editor.getDocument();
      if (doc.getLineCount() == 0) return;
      int caretOffset = editor.getCaretModel().getOffset();
      final int lineNumber = doc.getLineNumber(caretOffset);
      int lineEndOffset = doc.getLineEndOffset(lineNumber);

      if (caretOffset >= lineEndOffset) {
        DeleteLineAction.deleteLineAtCaret(editor);
        return;
      }

      copyToClipboard(doc, caretOffset, lineEndOffset, dataContext, editor);

      final int lineStartOffset = doc.getLineStartOffset(lineNumber);
      if (StringUtil.isEmptyOrSpaces(doc.getCharsSequence().subSequence(lineStartOffset, lineEndOffset).toString())) {
        DeleteLineAction.deleteLineAtCaret(editor);
      }
      else {
        doc.deleteString(caretOffset, lineEndOffset);
      }
    }

    private static void copyToClipboard(final Document doc,
                                        int caretOffset,
                                        int lineEndOffset,
                                        DataContext dataContext,
                                        Editor editor) {
      String s = doc.getCharsSequence().subSequence(caretOffset, lineEndOffset).toString();

      s = StringUtil.convertLineSeparators(s);
      StringSelection contents = new StringSelection(s);

      Project project = PlatformDataKeys.PROJECT.getData(dataContext);
      if (project == null) {
        Clipboard clipboard = editor.getComponent().getToolkit().getSystemClipboard();
        clipboard.setContents(contents, EmptyClipboardOwner.INSTANCE);
      }
      else {
        CopyPasteManager.getInstance().setContents(contents);
      }
    }
  }
}
