// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.editor.EditorCustomElementRenderer;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.InlayProperties;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;
import java.util.function.IntSupplier;

/**
 * @see com.intellij.openapi.editor.InlayModel#addBlockElement(int, boolean, boolean, int, com.intellij.openapi.editor.EditorCustomElementRenderer)
 */
final class BlockInlayImpl<R extends EditorCustomElementRenderer> extends InlayImpl<R, BlockInlayImpl<?>> implements IntSupplier {
  final boolean myShowAbove;
  final boolean myShowWhenFolded;
  final int myPriority;
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
    return myEditor.getInlayModel().myBlockElementsTree;
  }

  @Override
  void doUpdate() {
    myWidthInPixels = myRenderer.calcWidthInPixels(this);
    if (myWidthInPixels < 0) {
      throw PluginException.createByClass("Non-negative width should be defined for a block element by " + myRenderer, null,
                                          myRenderer.getClass());
    }
    int oldHeightInPixels = myHeightInPixels;
    myHeightInPixels = myRenderer.calcHeightInPixels(this);
    if (oldHeightInPixels != myHeightInPixels) getTree().valueUpdated(this);
    if (myHeightInPixels < 0) {
      throw PluginException.createByClass("Non-negative height should be defined for a block element by " + myRenderer, null,
                                          myRenderer.getClass());
    }
    myGutterIconRenderer = myRenderer.calcGutterIconRenderer(this);
  }

  @Override
  Point getPosition() {
    int visualLine = myEditor.offsetToVisualLine(getOffset());
    int[] yRange = myEditor.visualLineToYRange(visualLine);
    List<Inlay<?>> allInlays = myEditor.getInlayModel().getBlockElementsForVisualLine(visualLine, myShowAbove);
    int y;
    if (myShowAbove) {
      y = yRange[0];
      boolean found = false;
      for (Inlay<?> inlay : allInlays) {
        if (inlay == this) found = true;
        if (found) y -= inlay.getHeightInPixels();
      }
    }
    else {
      y = yRange[1];
      for (Inlay<?> inlay : allInlays) {
        if (inlay == this) break;
        y += inlay.getHeightInPixels();
      }
    }
    return new Point(myEditor.getContentComponent().getInsets().left, y);
  }

  @Override
  public int getHeightInPixels() {
    return myHeightInPixels;
  }

  @Override
  public @NotNull Placement getPlacement() {
    return myShowAbove ? Placement.ABOVE_LINE : Placement.BELOW_LINE;
  }

  @Override
  public @NotNull VisualPosition getVisualPosition() {
    return myEditor.offsetToVisualPosition(getOffset());
  }

  @Override
  public @Nullable GutterIconRenderer getGutterIconRenderer() {
    return myGutterIconRenderer;
  }

  @Override
  public int getAsInt() {
    return myHeightInPixels;
  }

  @Override
  public @NotNull InlayProperties getProperties() {
    return new InlayProperties()
      .relatesToPrecedingText(isRelatedToPrecedingText())
      .showAbove(myShowAbove)
      .showWhenFolded(myShowWhenFolded)
      .priority(myPriority);
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
