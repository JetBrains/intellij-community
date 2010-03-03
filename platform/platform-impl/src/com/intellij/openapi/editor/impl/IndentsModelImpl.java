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

/*
 * @author max
 */
package com.intellij.openapi.editor.impl;

import com.intellij.codeStyle.CodeStyleFacade;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.IndentGuideDescriptor;
import com.intellij.openapi.editor.IndentsModel;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.text.CharArrayUtil;
import gnu.trove.TIntArrayList;

public class IndentsModelImpl implements IndentsModel {
  private EditorImpl myEditor;
  private int myIndentSize = -1;

  public IndentsModelImpl(EditorImpl editor) {
    myEditor = editor;
  }

  public int getIndentLevel(int line) {
    int answer = getIndents(line, true, true);
    if (answer == 0) return 0;

    int prev;
    do {
      prev = getIndents(--line, true, false);
    }
    while (line > 0 && prev == answer);

    if (answer - 2 > prev) {
      return prev + 1;
    }

    return answer;
  }

  private int getIndents(int line, boolean goUp, boolean goDown) {
    DocumentImpl myDocument = (DocumentImpl)myEditor.getDocument();
    if (line <= 0 || line >= myDocument.getLineCount()) return 0;
    int lineStart = myDocument.getLineStartOffset(line);
    int lineEnd = myDocument.getLineEndOffset(line);

    CharSequence chars = myDocument.getCharsNoThreadCheck();
    int nonWhitespaceOffset = CharArrayUtil.shiftForward(chars, lineStart, " \t");
    if (nonWhitespaceOffset < lineEnd) {
      return myEditor.calcColumnNumber(nonWhitespaceOffset, line) / getIndentSize();
    }
    else {
      int upIndent = goUp ? getIndents(line - 1, true, false) : 100;
      int downIndent = goDown ? getIndents(line + 1, false, true) : 100;
      return Math.min(upIndent, downIndent);
    }
  }

  public int getIndentSize() {
    if (myIndentSize == -1) {
      Project project = myEditor.getProject();
      VirtualFile vFile = myEditor.getVirtualFile();
      if (project == null || project.isDisposed() || vFile == null) return EditorUtil.getTabSize(myEditor);
      myIndentSize = CodeStyleFacade.getInstance(project).getIndentSize(vFile.getFileType());
    }
    return myIndentSize;
  }

  public void assumeIndent(int line, int level) {
    throw new UnsupportedOperationException("assumeIndent is not implemented"); // TODO
  }

  public IndentGuideDescriptor getCaretIndentGuide() {
    EditorSettings settings = myEditor.getSettings();
    if (!settings.isIndentGuidesShown()) return null;

    final int indentSize = getIndentSize();
    if (indentSize == 0) return null;

    final LogicalPosition caretPosition = myEditor.getCaretModel().getLogicalPosition();
    int startLine = caretPosition.line;
    int endLine = startLine;
    final int caretIndent = caretPosition.column / indentSize;
    final int indents = getIndentLevel(startLine);

    if (caretIndent * indentSize != caretPosition.column) return null;
    if (caretIndent > indents|| indents == 0) return null;

    if (caretIndent > 0 && caretPosition.column % indentSize == 0) {
      while (startLine > 0) {
        if (getIndentLevel(startLine - 1) <= caretIndent) break;
        startLine--;
      }

      while (endLine < myEditor.getDocument().getLineCount() - 1) {
        if (getIndentLevel(endLine + 1) <= caretIndent) break;
        endLine++;
      }

      if (startLine < endLine) {
        return new IndentGuideDescriptor(caretIndent, startLine, endLine, indentSize);
      }
    }

    return null;
  }
}
