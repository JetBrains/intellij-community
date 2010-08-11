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
package com.intellij.openapi.editor.textarea;

import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.editor.event.CaretListener;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;

/**
 * @author yole
 */
public class TextComponentCaretModel implements CaretModel {
  private final JTextComponent myTextComponent;
  private final TextComponentEditor myEditor;

  public TextComponentCaretModel(final JTextComponent textComponent, TextComponentEditor editor) {
    myTextComponent = textComponent;
    myEditor = editor;
  }

  public void moveCaretRelatively(final int columnShift,
                                  final int lineShift,
                                  final boolean withSelection, final boolean blockSelection, final boolean scrollToCaret) {
    throw new UnsupportedOperationException("Not implemented");
  }

  public void moveToLogicalPosition(final LogicalPosition pos) {
    moveToOffset(myEditor.logicalPositionToOffset(pos), false);
  }

  public void moveToVisualPosition(final VisualPosition pos) {
    moveToLogicalPosition(myEditor.visualToLogicalPosition(pos));
  }

  @Override
  public void moveToOffset(int offset) {
    moveToOffset(offset, false);
  }

  public void moveToOffset(final int offset, boolean locateBeforeSoftWrap) {
    myTextComponent.setCaretPosition(Math.min(offset, myTextComponent.getText().length()));
  }

  public LogicalPosition getLogicalPosition() {
    int caretPos = myTextComponent.getCaretPosition();
    int line;
    int lineStart;
    if (myTextComponent instanceof JTextArea) {
      final JTextArea textArea = (JTextArea)myTextComponent;
      try {
        line = textArea.getLineOfOffset(caretPos);
        lineStart = textArea.getLineStartOffset(line);
      }
      catch (BadLocationException e) {
        throw new RuntimeException(e);
      }
    }
    else {
      line = 0;
      lineStart = 0;
    }
    return new LogicalPosition(line, caretPos - lineStart);
  }

  public VisualPosition getVisualPosition() {
    LogicalPosition pos = getLogicalPosition();
    return new VisualPosition(pos.line, pos.column);
  }

  public int getOffset() {
    return myTextComponent.getCaretPosition();
  }

  public void addCaretListener(final CaretListener listener) {
    throw new UnsupportedOperationException("Not implemented");
  }

  public void removeCaretListener(final CaretListener listener) {
    throw new UnsupportedOperationException("Not implemented");
  }

  public int getVisualLineStart() {
    return 0;
  }

  public int getVisualLineEnd() {
    return 0;
  }

  public TextAttributes getTextAttributes() {
    return null;
  }
}
