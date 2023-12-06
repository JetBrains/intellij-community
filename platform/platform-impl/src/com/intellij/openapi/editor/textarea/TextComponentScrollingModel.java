// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.textarea;

import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ScrollingModel;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import org.jetbrains.annotations.NotNull;

import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import java.awt.*;


final class TextComponentScrollingModel implements ScrollingModel {
  private final JTextComponent myTextComponent;

  TextComponentScrollingModel(@NotNull JTextComponent textComponent) {
    myTextComponent = textComponent;
  }

  @Override
  public @NotNull Rectangle getVisibleArea() {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public @NotNull Rectangle getVisibleAreaOnScrollingFinished() {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public void scrollToCaret(final @NotNull ScrollType scrollType) {
    final int position = myTextComponent.getCaretPosition();
    try {
      final Rectangle rectangle = myTextComponent.modelToView(position);
      if (rectangle != null) myTextComponent.scrollRectToVisible(rectangle);
    }
    catch (BadLocationException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void scrollTo(final @NotNull LogicalPosition pos, final @NotNull ScrollType scrollType) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public void runActionOnScrollingFinished(final @NotNull Runnable action) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public void disableAnimation() {
  }

  @Override
  public void enableAnimation() {
  }

  @Override
  public int getVerticalScrollOffset() {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public int getHorizontalScrollOffset() {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public void scrollVertically(final int scrollOffset) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public void scrollHorizontally(final int scrollOffset) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public void scroll(int horizontalOffset, int verticalOffset) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public void addVisibleAreaListener(final @NotNull VisibleAreaListener listener) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public void removeVisibleAreaListener(final @NotNull VisibleAreaListener listener) {
    throw new UnsupportedOperationException("Not implemented");
  }
}
