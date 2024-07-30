// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.impl.EditorGutterListener;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;

public abstract class EditorGutterComponentEx extends JComponent implements EditorGutter {
  /**
   * The key to retrieve the logical editor line position of the latest actionable click inside the gutter.
   * Available to gutter popup actions,
   * see {@link #setGutterPopupGroup(ActionGroup)},
   * {@link GutterIconRenderer#getPopupMenuActions()},
   * {@link TextAnnotationGutterProvider#getPopupActions(int, Editor)}.
   */
  public static final DataKey<Integer> LOGICAL_LINE_AT_CURSOR = DataKey.create("EditorGutter.LOGICAL_LINE_AT_CURSOR");

  /**
   * The key to retrieve the editor gutter icon's center position of the latest actionable click inside the gutter.
   * Available to gutter popup actions,
   * see {@link #setGutterPopupGroup(ActionGroup)},
   * {@link GutterIconRenderer#getPopupMenuActions()},
   * {@link TextAnnotationGutterProvider#getPopupActions(int, Editor)}.
   */
  public static final DataKey<Point> ICON_CENTER_POSITION = DataKey.create("EditorGutter.ICON_CENTER_POSITION");

  public abstract @NotNull EditorEx getEditor();

  public abstract @Nullable FoldRegion findFoldingAnchorAt(int x, int y);

  public abstract @NotNull List<GutterMark> getGutterRenderers(int line);

  public abstract @NotNull List<Pair<GutterMark, Rectangle>> getGutterRenderersAndRectangles(int visualLine);

  public abstract void addEditorGutterListener(@NotNull EditorGutterListener listener, @NotNull Disposable parentDisposable);

  public abstract @Nullable EditorGutterAction getAction(@NotNull TextAnnotationGutterProvider provider);

  public abstract int getWhitespaceSeparatorOffset();

  public abstract void revalidateMarkup();

  public abstract int getLineMarkerAreaOffset();

  public abstract int getIconAreaOffset();

  public abstract int getLineMarkerFreePaintersAreaOffset();

  public abstract int getIconsAreaWidth();

  public abstract int getAnnotationsAreaOffset();

  public abstract int getAnnotationsAreaWidth();

  public abstract int getFoldingAreaOffset();

  public abstract @Nullable Point getCenterPoint(GutterIconRenderer renderer);

  public abstract void setShowDefaultGutterPopup(boolean show);

  /** When set to false, makes {@link #closeAllAnnotations()} a no-op and hides the corresponding context menu action. */
  public abstract void setCanCloseAnnotations(boolean canCloseAnnotations);

  public abstract void setGutterPopupGroup(@Nullable ActionGroup group);

  @ApiStatus.Experimental
  public abstract @NotNull List<AnAction> getTextAnnotationPopupActions(int logicalLine);

  public abstract boolean isPaintBackground();

  public abstract void setPaintBackground(boolean value);

  /**
   * Force left free painters area to always be visible or hidden
   */
  @ApiStatus.Experimental
  @ApiStatus.Internal
  public abstract void setLeftFreePaintersAreaState(@NotNull EditorGutterFreePainterAreaState value);

  /**
   * Force right free painters area to always be visible or hidden
   */
  @ApiStatus.Experimental
  @ApiStatus.Internal
  public abstract void setRightFreePaintersAreaState(@NotNull EditorGutterFreePainterAreaState value);

  /**
   * Request an area in the left area to be reserved
   */
  @ApiStatus.Experimental
  @ApiStatus.Internal
  public abstract void reserveLeftFreePaintersAreaWidth(@NotNull Disposable disposable, int widthInPixels);

  /**
   * Request an area in the right area to be reserved
   */
  @ApiStatus.Experimental
  @ApiStatus.Internal
  public abstract void reserveRightFreePaintersAreaWidth(@NotNull Disposable disposable, int widthInPixels);

  public abstract void setInitialIconAreaWidth(int width);

  public abstract @Nullable GutterMark getGutterRenderer(Point p);

  public abstract @Nullable Runnable setLoadingIconForCurrentGutterMark();

  @ApiStatus.Internal
  public abstract int getExtraLineMarkerFreePaintersAreaOffset();

  @ApiStatus.Internal
  public abstract boolean isInsideMarkerArea(@NotNull MouseEvent e);

  @ApiStatus.Internal
  public int getHoveredFreeMarkersLine() {
    return -1;
  }

  @ApiStatus.Internal
  public int getLineNumberAreaOffset() {
    return 0;
  }

  @ApiStatus.Internal
  public int getLineNumberAreaWidth() {
    return 0;
  }

  public @NotNull LineNumberConverter getPrimaryLineNumberConverter() {
    return LineNumberConverter.DEFAULT;
  }

  public @Nullable LineNumberConverter getAdditionalLineNumberConverter() {
    return null;
  }
}
