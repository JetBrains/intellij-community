// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.editor.EditorCustomElementRenderer;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;
import java.util.function.IntSupplier;

final class BlockInlayImpl<R extends EditorCustomElementRenderer> extends InlayImpl<R, BlockInlayImpl<?>> implements IntSupplier {
  final boolean myShowAbove;
  final int myPriority;
  private int myHeightInPixels;
  private GutterIconRenderer myGutterIconRenderer;

  BlockInlayImpl(@NotNull EditorImpl editor,
                 int offset,
                 boolean relatesToPrecedingText,
                 boolean showAbove,
                 int priority,
                 @NotNull R renderer) {
    super(editor, offset, relatesToPrecedingText, renderer);
    myShowAbove = showAbove;
    myPriority = priority;
  }

  @Override
  MarkerTreeWithPartialSums<BlockInlayImpl<?>> getTree() {
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
    int y = myEditor.visualLineToY(visualLine);
    List<Inlay<?>> allInlays = myEditor.getInlayModel().getBlockElementsForVisualLine(visualLine, myShowAbove);
    if (myShowAbove) {
      boolean found = false;
      for (Inlay<?> inlay : allInlays) {
        if (inlay == this) found = true;
        if (found) y -= inlay.getHeightInPixels();
      }
    }
    else {
      y += myEditor.getLineHeight();
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

  @NotNull
  @Override
  public Placement getPlacement() {
    return myShowAbove ? Placement.ABOVE_LINE : Placement.BELOW_LINE;
  }

  @NotNull
  @Override
  public VisualPosition getVisualPosition() {
    return myEditor.offsetToVisualPosition(getOffset());
  }

  @Nullable
  @Override
  public GutterIconRenderer getGutterIconRenderer() {
    return myGutterIconRenderer;
  }

  @Override
  public int getAsInt() {
    return myHeightInPixels;
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
