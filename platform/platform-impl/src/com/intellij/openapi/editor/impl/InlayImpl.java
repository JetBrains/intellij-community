// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.CustomWrap;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorCustomElementRenderer;
import com.intellij.openapi.editor.EditorThreading;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.InlayModel;
import com.intellij.openapi.editor.InlayProperties;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.ex.RangeMarkerEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import java.util.Objects;

import static com.intellij.openapi.editor.impl.InlayKeys.ID_BEFORE_DISPOSAL;
import static com.intellij.openapi.editor.impl.InlayKeys.OFFSET_BEFORE_DISPOSAL;

abstract class InlayImpl<R extends EditorCustomElementRenderer, T extends InlayImpl<?, ?>>
  extends RangeMarkerImpl implements EditorInlay<R> {

  final @NotNull EditorImpl myEditor;
  final @NotNull R myRenderer;
  private final boolean myRelatedToPrecedingText;

  int myWidthInPixels;

  @SuppressWarnings("AbstractMethodCallInConstructor")
  InlayImpl(@NotNull EditorImpl editor, int offset, boolean relatesToPrecedingText, @NotNull R renderer) {
    super(editor.getElfDocument(), offset, offset, false, true);
    myEditor = editor;
    myRelatedToPrecedingText = relatesToPrecedingText;
    myRenderer = renderer;
    doUpdate();
    //noinspection unchecked
    getTree().addInterval((T)this, offset, offset, false, false, relatesToPrecedingText, 0);
  }

  @ApiStatus.Internal
  public abstract RangeMarkerTree<T> getTree();

  @Override
  public @NotNull EditorImpl getEditorImpl() {
    return myEditor;
  }

  @Override
  public boolean isValid() {
    return !myEditor.isDisposed() && super.isValid();
  }

  @Override
  public void setWidthInPixels(int widthInPixels) {
    myWidthInPixels = widthInPixels;
  }

  /**
   * WARNING: for legacy reasons implements both {@link Disposable#dispose()} and {@link RangeMarker#dispose()}.
   * These have different contracts.
   * <p>
   * We rely on {@link IntervalTreeImpl#fireAfterRemoved(RangeMarkerEx)} for proper {@link Disposable} disposal.
   */
  @Override
  public void dispose() {
    EditorImpl.assertIsDispatchThread();
    if (isValid()) {
      int offset = getOffset(); // We want listeners notified after disposal, but want inlay offset to be available at that time
      putUserData(OFFSET_BEFORE_DISPOSAL, offset);
      putUserData(ID_BEFORE_DISPOSAL, getId());
      //noinspection unchecked
      getTree().removeInterval((T)this);
      myEditor.getInlayModel().notifyRemoved(this);
    }
  }

  @Override
  public boolean isRelatedToPrecedingText() {
    return myRelatedToPrecedingText;
  }

  @Override
  public @NotNull R getRenderer() {
    return myRenderer;
  }

  @Override
  public int getWidthInPixels() {
    return myWidthInPixels;
  }
}

interface EditorInlay<R extends EditorCustomElementRenderer> extends Inlay<R>, RangeMarkerEx {
  @NotNull EditorImpl getEditorImpl();

  void doUpdate();

  @NotNull Point getPosition();

  void setWidthInPixels(int widthInPixels);

  @Override
  default @NotNull Editor getEditor() {
    return getEditorImpl();
  }

  @Override
  default int getOffset() {
    Integer offsetBeforeDisposal = getUserData(OFFSET_BEFORE_DISPOSAL);
    return offsetBeforeDisposal == null ? getStartOffset() : offsetBeforeDisposal;
  }

  @Override
  default @Nullable Rectangle getBounds() {
    if (EditorUtil.isInlayFolded(this)) return null;
    Point position = getPosition();
    return new Rectangle(position.x, position.y, getWidthInPixels(), getHeightInPixels());
  }

  @Override
  default @Nullable GutterIconRenderer getGutterIconRenderer() {
    return null;
  }

  @Override
  default void update() {
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
      getEditorImpl().getInlayModel().notifyChanged(this, changeFlags);
    }
    else {
      repaint();
    }
  }

  @Override
  default void repaint() {
    EditorImpl editor = getEditorImpl();
    if (isValid() && !editor.isDisposed() && !editor.getElfDocument().isInBulkUpdate() && !editor.getInlayModel().isInBatchMode()) {
      JComponent contentComponent = editor.getContentComponent();
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
}

interface InlineInlay<R extends EditorCustomElementRenderer> extends EditorInlay<R> {
  int getPriority();

  int getOrder();

  @Override
  default void doUpdate() {
    R renderer = getRenderer();
    int width = renderer.calcWidthInPixels(this);
    setWidthInPixels(width);
    if (width <= 0) {
      throw PluginException.createByClass(
        "Positive width should be defined for an inline element by " + renderer +
        " (class=" + renderer.getClass().getName() + ", valid=" + isValid() + ", myWidthInPixels=" + width + ")",
        null, renderer.getClass()
      );
    }
  }

  @Override
  default @NotNull Placement getPlacement() {
    return Placement.INLINE;
  }

  @Override
  default @NotNull VisualPosition getVisualPosition() {
    EditorImpl editor = getEditorImpl();
    int offset = getOffset();
    List<Inlay<?>> inlays = editor.getInlayModel().getInlineElementsInRange(offset, offset);
    List<CustomWrap> customWraps = editor.getCustomWrapModel().getWrapsAtOffset(offset);
    if (customWraps.isEmpty()) {
      VisualPosition position = editor.offsetToVisualPosition(offset, false, false);
      int order = inlays.indexOf(this);
      return new VisualPosition(position.line, position.column + order, true);
    }
    else {
      int firstRelatedToPrecedingIndex = ContainerUtil.indexOf(inlays, inlay -> inlay.isRelatedToPrecedingText());
      firstRelatedToPrecedingIndex = firstRelatedToPrecedingIndex >= 0 ? firstRelatedToPrecedingIndex : inlays.size();
      VisualPosition position = editor.offsetToVisualPosition(offset, false, isRelatedToPrecedingText());
      int order = inlays.indexOf(this);
      int precedingInlayCount = isRelatedToPrecedingText() ? firstRelatedToPrecedingIndex : 0;
      return new VisualPosition(position.line, position.column + order - precedingInlayCount, true);
    }
  }

  @Override
  default @NotNull Point getPosition() {
    return getEditorImpl().visualPositionToXY(getVisualPosition());
  }

  @Override
  default int getHeightInPixels() {
    return getEditorImpl().getLineHeight();
  }

  @Override
  default @NotNull InlayProperties getProperties() {
    return new InlayProperties()
      .relatesToPrecedingText(isRelatedToPrecedingText())
      .priority(getPriority());
  }
}

interface AfterLineEndInlay<R extends EditorCustomElementRenderer> extends EditorInlay<R> {
  boolean isSoftWrappable();

  int getPriority();

  int getOrder();

  @Override
  default void doUpdate() {
    R renderer = getRenderer();
    int width = renderer.calcWidthInPixels(this);
    setWidthInPixels(width);
    if (width <= 0) {
      throw PluginException.createByClass("Positive width should be defined for an after-line-end element by " + renderer, null,
                                          renderer.getClass());
    }
  }

  @Override
  default @NotNull Point getPosition() {
    VisualPosition position = EditorThreading.compute(this::getVisualPosition);
    return getEditorImpl().visualPositionToXY(position);
  }

  @Override
  default @NotNull Placement getPlacement() {
    return Placement.AFTER_LINE_END;
  }

  @Override
  default @NotNull VisualPosition getVisualPosition() {
    EditorImpl editor = getEditorImpl();
    int offset = getOffset();
    int logicalLine = editor.getDocument().getLineNumber(offset);
    int lineEndOffset = editor.getDocument().getLineEndOffset(logicalLine);
    VisualPosition position = editor.offsetToVisualPosition(lineEndOffset, true, true);
    if (editor.getFoldingModel().isOffsetCollapsed(lineEndOffset)) return position;
    List<Inlay<?>> inlays = editor.getInlayModel().getAfterLineEndElementsForLogicalLine(logicalLine);
    int order = inlays.indexOf(this);
    return new VisualPosition(position.line, position.column + 1 + order);
  }

  @Override
  default int getHeightInPixels() {
    return getEditorImpl().getLineHeight();
  }

  @Override
  default @NotNull InlayProperties getProperties() {
    return new InlayProperties()
      .relatesToPrecedingText(isRelatedToPrecedingText())
      .disableSoftWrapping(!isSoftWrappable())
      .priority(getPriority());
  }
}
