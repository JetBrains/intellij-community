/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.editor.ScrollingModel;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import org.jetbrains.annotations.NotNull;

import javax.swing.text.JTextComponent;
import javax.swing.text.BadLocationException;
import java.awt.*;

/**
 * @author yole
 */
public class TextComponentScrollingModel implements ScrollingModel {
  private final JTextComponent myTextComponent;

  public TextComponentScrollingModel(final JTextComponent textComponent) {
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
      myTextComponent.scrollRectToVisible(rectangle);
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
  public void addVisibleAreaListener(@NotNull final VisibleAreaListener listener) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public void removeVisibleAreaListener(@NotNull final VisibleAreaListener listener) {
    throw new UnsupportedOperationException("Not implemented");
  }
}
