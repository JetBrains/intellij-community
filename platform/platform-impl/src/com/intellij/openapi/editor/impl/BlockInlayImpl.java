// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.EditorCustomElementRenderer;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @see com.intellij.openapi.editor.InlayModel#addBlockElement(int, boolean, boolean, int, com.intellij.openapi.editor.EditorCustomElementRenderer)
 */
final class BlockInlayImpl<R extends EditorCustomElementRenderer> extends InlayImpl<R, BlockInlayImpl<?>> implements BlockInlay<R> {
  private final boolean myShowAbove;
  private final boolean myShowWhenFolded;
  private final int myPriority;
  private int myHeightInPixels;
  private GutterIconRenderer myGutterIconRenderer;

  BlockInlayImpl(@NotNull EditorImpl editor,
                 int offset,
                 boolean relatesToPrecedingText,
                 boolean showAbove,
                 boolean showWhenFolded,
                 int priority,
                 @NotNull R renderer) {
    super(editor, offset, relatesToPrecedingText, renderer);
    myShowAbove = showAbove;
    myShowWhenFolded = showWhenFolded;
    myPriority = priority;
  }

  @Override
  @ApiStatus.Internal
  public MarkerTreeWithPartialSums<BlockInlayImpl<?>> getTree() {
    return myEditor.getInlayModel().getBlockElementsTree();
  }

  @Override
  public void doUpdate() {
    BlockInlay.super.doUpdate();
  }

  @Override
  public int getPriority() {
    return myPriority;
  }

  @Override
  public boolean isShownAbove() {
    return myShowAbove;
  }

  @Override
  public boolean isShownWhenFolded() {
    return myShowWhenFolded;
  }

  @Override
  public void setHeightInPixels(int heightInPixels) {
    if (myHeightInPixels == heightInPixels) return;
    myHeightInPixels = heightInPixels;
    getTree().valueUpdated(this);
  }

  @Override
  public int getHeightInPixels() {
    return myHeightInPixels;
  }

  @Override
  public @Nullable GutterIconRenderer getGutterIconRenderer() {
    return myGutterIconRenderer;
  }

  @Override
  public void setGutterIconRenderer(@Nullable GutterIconRenderer gutterIconRenderer) {
    myGutterIconRenderer = gutterIconRenderer;
  }

  @Override
  public String toString() {
    return "[Block inlay, offset=" + getOffset() +
           ", width=" + myWidthInPixels +
           ", height=" + myHeightInPixels +
           ", renderer=" + myRenderer +
           "]" + (isValid() ? "" : "(invalid)");
  }
}
