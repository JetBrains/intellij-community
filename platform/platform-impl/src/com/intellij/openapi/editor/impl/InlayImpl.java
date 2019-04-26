// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorCustomElementRenderer;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

abstract class InlayImpl<R extends EditorCustomElementRenderer, T extends InlayImpl> extends RangeMarkerWithGetterImpl implements Inlay<R> {
  static final Key<Integer> OFFSET_BEFORE_DISPOSAL = Key.create("inlay.offset.before.disposal");

  @NotNull
  final EditorImpl myEditor;
  @NotNull
  final R myRenderer;
  private final boolean myRelatedToPrecedingText;

  int myWidthInPixels;

  @SuppressWarnings("AbstractMethodCallInConstructor")
  InlayImpl(@NotNull EditorImpl editor, int offset, boolean relatesToPrecedingText, @NotNull R renderer) {
    super(editor.getDocument(), offset, offset, false);
    myEditor = editor;
    myRelatedToPrecedingText = relatesToPrecedingText;
    myRenderer = renderer;
    doUpdateSize();
    //noinspection unchecked
    getTree().addInterval((T)this, offset, offset, false, false, relatesToPrecedingText, 0);
  }

  abstract RangeMarkerTree<T> getTree();

  @NotNull
  @Override
  public Editor getEditor() {
    return myEditor;
  }

  @Override
  public void updateSize() {
    int oldWidth = getWidthInPixels();
    int oldHeight = getHeightInPixels();
    doUpdateSize();
    if (oldWidth != getWidthInPixels() || oldHeight != getHeightInPixels()) {
      myEditor.getInlayModel().notifyChanged(this);
    }
    else {
      repaint();
    }
  }

  @Override
  public void repaint() {
    if (isValid() && !myEditor.isDisposed() && !myEditor.getDocument().isInBulkUpdate()) {
      JComponent contentComponent = myEditor.getContentComponent();
      if (contentComponent.isShowing()) {
        Rectangle bounds = getBounds();
        if (bounds != null) {
          if (this instanceof BlockInlayImpl) {
            bounds.width = contentComponent.getWidth();
          }
          contentComponent.repaint(bounds);
        }
      }
    }
  }

  abstract void doUpdateSize();

  @Override
  public void dispose() {
    if (isValid()) {
      int offset = getOffset(); // We want listeners notified after disposal, but want inlay offset to be available at that time
      putUserData(OFFSET_BEFORE_DISPOSAL, offset);
      //noinspection unchecked
      getTree().removeInterval((T)this);
      myEditor.getInlayModel().notifyRemoved(this);
    }
  }

  @Override
  public int getOffset() {
    Integer offsetBeforeDisposal = getUserData(OFFSET_BEFORE_DISPOSAL);
    return offsetBeforeDisposal == null ? getStartOffset() : offsetBeforeDisposal;
  }

  @Override
  public boolean isRelatedToPrecedingText() {
    return myRelatedToPrecedingText;
  }

  abstract Point getPosition();

  @Nullable
  @Override
  public Rectangle getBounds() {
    if (myEditor.getFoldingModel().isOffsetCollapsed(getOffset())) return null;
    Point pos = getPosition();
    return new Rectangle(pos.x, pos.y, getWidthInPixels(), getHeightInPixels());
  }

  @NotNull
  @Override
  public R getRenderer() {
    return myRenderer;
  }

  @Override
  public int getWidthInPixels() {
    return myWidthInPixels;
  }
}
