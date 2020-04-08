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

import javax.swing.*;
import javax.swing.text.BadLocationException;

/**
 * @author yole
 */
class TextAreaDocument extends TextComponentDocument {
  private final JTextArea myTextArea;

  TextAreaDocument(final JTextArea textComponent) {
    super(textComponent);
    myTextArea = textComponent;
  }

  @Override
  public int getLineCount() {
    return myTextArea.getLineCount();
  }

  @Override
  public int getLineNumber(final int offset) {
    try {
      return myTextArea.getLineOfOffset(offset);
    }
    catch (BadLocationException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public int getLineStartOffset(final int line) {
    try {
      return myTextArea.getLineStartOffset(line);
    }
    catch (BadLocationException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public int getLineEndOffset(final int line) {
    try {
      return myTextArea.getLineEndOffset(line) - getLineSeparatorLength(line);
    }
    catch (BadLocationException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public int getLineSeparatorLength(final int line) {
    if (line == myTextArea.getLineCount()-1) {
      return 0;
    }
    try {
      int l = 0;
      String text = getText();
      for (int pos = myTextArea.getLineEndOffset(line) - 1; pos >= myTextArea.getLineStartOffset(line); pos--) {
        if (text.charAt(pos) != '\r' && text.charAt(pos) != '\n') break;
        l++;
      }
      return l;
    }
    catch (BadLocationException e) {
      throw new RuntimeException(e);
    }
  }
}
