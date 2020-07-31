// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorCustomElementRenderer;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.InlayModel;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

abstract class InlayImpl<R extends EditorCustomElementRenderer, T extends InlayImpl<?, ?>> extends RangeMarkerImpl implements Inlay<R> {
  static final Key<Integer> OFFSET_BEFORE_DISPOSAL = Key.create("inlay.offset.before.disposal");

  @NotNull
  final EditorImpl myEditor;
  @NotNull
  final R myRenderer;
  private final boolean myRelatedToPrecedingText;

  int myWidthInPixels;

  @SuppressWarnings("AbstractMethodCallInConstructor")
  InlayImpl(@NotNull EditorImpl editor, int offset, boolean relatesToPrecedingText, @NotNull R renderer) {
    super(editor.getDocument(), offset, offset, false, true);
    myEditor = editor;
    myRelatedToPrecedingText = relatesToPrecedingText;
    myRenderer = renderer;
    doUpdate();
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
  public boolean isValid() {
    return !myEditor.isDisposed() && super.isValid();
  }

  @Override
  public void update() {
    EditorImpl.assertIsDispatchThread();
    int oldWidth = getWidthInPixels();
    int oldHeight = getHeightInPixels();
    GutterIconRenderer oldIconRenderer = getGutterIconRenderer();
    doUpdate();
    int changeFlags = 0;
    if (oldWidth != getWidthInPixels()) changeFlags |= InlayModel.ChangeFlags.WIDTH_CHANGED;
    if (oldHeight != getHeightInPixels()) changeFlags |= InlayModel.ChangeFlags.HEIGHT_CHANGED;
    if (!Objects.equals(oldIconRenderer, getGutterIconRenderer())) changeFlags |= InlayModel.ChangeFlags.GUTTER_ICON_PROVIDER_CHANGED;
    if (changeFlags != 0) {
      myEditor.getInlayModel().notifyChanged(this, changeFlags);
    }
    else {
      repaint();
    }
  }

  @Override
  public void repaint() {
    if (isValid() && !myEditor.isDisposed() && !myEditor.getDocument().isInBulkUpdate() && !myEditor.getInlayModel().isInBatchMode()) {
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

  abstract void doUpdate();

  @Override
  public void dispose() {
    EditorImpl.assertIsDispatchThread();
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
    if (EditorUtil.isInlayFolded(this)) return null;
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

  @Nullable
  @Override
  public GutterIconRenderer getGutterIconRenderer() {
    return null;
  }
}
