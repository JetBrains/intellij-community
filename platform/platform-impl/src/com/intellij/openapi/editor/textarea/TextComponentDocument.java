// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.textarea;

import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolderBase;
import org.jetbrains.annotations.NotNull;

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;


class TextComponentDocument extends UserDataHolderBase implements com.intellij.openapi.editor.Document {
  private final JTextComponent myTextComponent;

  TextComponentDocument(final JTextComponent textComponent) {
    myTextComponent = textComponent;
  }

  @Override
  public @NotNull CharSequence getImmutableCharSequence() {
    try {
      final Document document = myTextComponent.getDocument();
      return document.getText(0, document.getLength());
    }
    catch (BadLocationException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public @NotNull String getText(@NotNull TextRange range) {
    try {
      final Document document = myTextComponent.getDocument();
      return document.getText(range.getStartOffset(), range.getLength());
    }
    catch (BadLocationException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public int getTextLength() {
    return myTextComponent.getDocument().getLength();
  }

  @Override
  public int getLineCount() {
    return 1;
  }

  @Override
  public int getLineNumber(final int offset) {
    return 0;
  }

  @Override
  public int getLineStartOffset(final int line) {
    return 0;
  }

  @Override
  public int getLineEndOffset(final int line) {
    return getTextLength();
  }

  @Override
  public void insertString(final int offset, final @NotNull CharSequence s) {
    try {
      myTextComponent.getDocument().insertString(offset, s.toString(), null);
    }
    catch (BadLocationException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void deleteString(final int startOffset, final int endOffset) {
    try {
      myTextComponent.getDocument().remove(startOffset, endOffset - startOffset);
    }
    catch (BadLocationException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void replaceString(final int startOffset, final int endOffset, final @NotNull CharSequence s) {
    final Document document = myTextComponent.getDocument();
    try {
      document.remove(startOffset, endOffset-startOffset);
      document.insertString(startOffset, s.toString(), null);
    }
    catch (BadLocationException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public boolean isWritable() {
    return true;
  }

  @Override
  public @NotNull RangeMarker createRangeMarker(int startOffset, int endOffset, boolean surviveOnExternalChange) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public @NotNull RangeMarker createGuardedBlock(int startOffset, int endOffset) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public void setText(@NotNull CharSequence text) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public long getModificationStamp() {
    throw new UnsupportedOperationException("Not implemented");
  }
}