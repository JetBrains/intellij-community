// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.textarea;

import javax.swing.*;
import javax.swing.text.BadLocationException;


final class TextAreaDocument extends TextComponentDocument {
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
