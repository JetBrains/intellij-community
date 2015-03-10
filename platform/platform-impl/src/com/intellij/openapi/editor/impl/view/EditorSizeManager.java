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
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.EditorLinePainter;
import com.intellij.openapi.editor.LineExtensionInfo;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener;
import com.intellij.openapi.editor.impl.EditorDocumentPriorities;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.awt.*;
import java.util.Collection;

class EditorSizeManager implements PrioritizedDocumentListener, Disposable {
  private final EditorView myView;
  private final EditorImpl myEditor;
  private final DocumentEx myDocument;
  
  private int myWidthInPixels;

  private int myMaxLineWithExtensionWidth;
  private int myWidestLineWithExtension;
  
  EditorSizeManager(EditorView view) {
    myView = view;
    myEditor = view.getEditor();
    myDocument = myEditor.getDocument(); 
    myDocument.addDocumentListener(this, this);
  }

  @Override
  public void dispose() {
  }

  @Override
  public int getPriority() {
    return EditorDocumentPriorities.EDITOR_TEXT_WIDTH_CACHE;
  }

  @Override
  public void beforeDocumentChange(DocumentEvent event) {
  }

  @Override
  public void documentChanged(DocumentEvent event) {
    invalidateCachedWidth();
  }

  Dimension getPreferredSize() {
    int width = getPreferredWidth();
    if (!myDocument.isInBulkUpdate()) {
      for (Caret caret : myEditor.getCaretModel().getAllCarets()) {
        if (caret.isUpToDate()) {
          int caretX = myView.visualPositionToXY(caret.getVisualPosition(), true).x;
          width = Math.max(caretX, width);
        }
      }
    }
    width += myEditor.getSettings().getAdditionalColumnsCount() * myView.getPlainSpaceWidth();
    return new Dimension(width, myEditor.getPreferredHeight());
  }

  private int getPreferredWidth() {
    if (myWidthInPixels < 0) {
      myWidthInPixels = calculatePreferredWidth();
    }
    validateMaxLineWithExtension();
    return Math.max(myWidthInPixels, myMaxLineWithExtensionWidth);
  }

  private void validateMaxLineWithExtension() {
    if (myMaxLineWithExtensionWidth > 0) {
      Project project = myEditor.getProject();
      VirtualFile virtualFile = myEditor.getVirtualFile();
      if (project != null && virtualFile != null) {
        for (EditorLinePainter painter : EditorLinePainter.EP_NAME.getExtensions()) {
          Collection<LineExtensionInfo> extensions = painter.getLineExtensions(project, virtualFile, myWidestLineWithExtension);
          if (extensions != null && !extensions.isEmpty()) {
            return;
          }
        }
      }
      myMaxLineWithExtensionWidth = 0;
    }
  }

  private int calculatePreferredWidth() {
    int lineCount = myDocument.getLineCount();
    int maxWidth = (lineCount == 0 ? 0 : myView.getLineWidth(0));
    for (int line = 1; line < lineCount; line++) {
      LineLayout lineLayout = myView.getCachedLineLayout(line);
      if (lineLayout != null) {
        maxWidth = Math.max(maxWidth, (int)lineLayout.getMaxX());
      }
    }
    int longestLineNumber = guessLongestLineNumber();
    if (longestLineNumber > 0) {
      maxWidth = Math.max(maxWidth, myView.getLineWidth(longestLineNumber));
    }
    return maxWidth;
  }

  private int guessLongestLineNumber() {
    int lineCount = myDocument.getLineCount();
    int longestLineNumber = -1;
    int longestLine = -1;
    for (int line = 0; line < lineCount; line++) {
      int lineChars = myDocument.getLineEndOffset(line) - myDocument.getLineStartOffset(line);
      if (lineChars > longestLine) {
        longestLine = lineChars;
        longestLineNumber = line;
      }
    }
    return longestLineNumber;
  }

  void invalidateCachedWidth() {
    myWidthInPixels = -1;
  }

  int getMaxLineWithExtensionWidth() {
    return myMaxLineWithExtensionWidth;
  }

  void setMaxLineWithExtensionWidth(int lineNumber, int width) {
    myWidestLineWithExtension = lineNumber;
    myMaxLineWithExtensionWidth = width;
  }
  
  void validateCurrentWidth(LineLayout lineLayout) {
    int width = (int)lineLayout.getMaxX();
    if (myWidthInPixels >= 0 && width > myWidthInPixels) {
      myWidthInPixels = width;
      myEditor.getContentComponent().revalidate();
    }
  }
}
