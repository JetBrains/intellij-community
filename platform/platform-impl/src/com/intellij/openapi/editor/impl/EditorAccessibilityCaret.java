// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.ChangeListener;
import javax.swing.text.Caret;
import javax.swing.text.DefaultCaret;
import javax.swing.text.JTextComponent;
import java.awt.*;

/**
 * {@linkplain DefaultCaret} does a lot of work we don't want (listening for focus events etc).
 * This exists simply to be able to send caret events to the screen reader.
 */
final class EditorAccessibilityCaret implements Caret {

  private final Editor myEditor;

  EditorAccessibilityCaret(@NotNull Editor editor) {
    myEditor = editor;
  }

  @Override
  public void install(JTextComponent jTextComponent) {
  }

  @Override
  public void deinstall(JTextComponent jTextComponent) {
  }

  @Override
  public void paint(Graphics graphics) {
  }

  @Override
  public void addChangeListener(ChangeListener changeListener) {
  }

  @Override
  public void removeChangeListener(ChangeListener changeListener) {
  }

  @Override
  public boolean isVisible() {
    return true;
  }

  @Override
  public void setVisible(boolean visible) {
  }

  @Override
  public boolean isSelectionVisible() {
    return true;
  }

  @Override
  public void setSelectionVisible(boolean visible) {
  }

  @Override
  public void setMagicCaretPosition(Point point) {
  }

  @Override
  public @Nullable Point getMagicCaretPosition() {
    return null;
  }

  @Override
  public void setBlinkRate(int rate) {
  }

  @Override
  public int getBlinkRate() {
    return 250;
  }

  @Override
  public int getDot() {
    return ReadAction.compute(() -> myEditor.getCaretModel().getOffset());
  }

  @Override
  public int getMark() {
    return ReadAction.compute(() -> myEditor.getSelectionModel().getSelectionStart());
  }

  @Override
  public void setDot(int offset) {
    if (!myEditor.isDisposed()) {
      myEditor.getCaretModel().moveToOffset(offset);
    }
  }

  @Override
  public void moveDot(int offset) {
    if (!myEditor.isDisposed()) {
      myEditor.getCaretModel().moveToOffset(offset);
    }
  }
}
