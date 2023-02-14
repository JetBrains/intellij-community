// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.textarea;

import com.intellij.openapi.editor.ScrollingModel;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import org.jetbrains.annotations.NotNull;

import javax.swing.text.JTextComponent;
import javax.swing.text.BadLocationException;
import java.awt.*;


class TextComponentScrollingModel implements ScrollingModel {
  private final JTextComponent myTextComponent;

  TextComponentScrollingModel(@NotNull JTextComponent textComponent) {
    myTextComponent = textComponent;
  }

  @NotNull
  @Override
  public Rectangle getVisibleArea() {
    throw new UnsupportedOperationException("Not implemented");
  }

  @NotNull
  @Override
  public Rectangle getVisibleAreaOnScrollingFinished() {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public void scrollToCaret(@NotNull final ScrollType scrollType) {
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
  public void scrollTo(@NotNull final LogicalPosition pos, @NotNull final ScrollType scrollType) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public void runActionOnScrollingFinished(@NotNull final Runnable action) {
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
  public void addVisibleAreaListener(@NotNull final VisibleAreaListener listener) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public void removeVisibleAreaListener(@NotNull final VisibleAreaListener listener) {
    throw new UnsupportedOperationException("Not implemented");
  }
}
