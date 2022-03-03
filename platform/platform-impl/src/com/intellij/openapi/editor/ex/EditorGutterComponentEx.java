// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorGutter;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.TextAnnotationGutterProvider;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;

public abstract class EditorGutterComponentEx extends JComponent implements EditorGutter {
  /**
   * The key to retrieve a logical editor line position of a latest actionable click inside the gutter.
   * Available to gutter popup actions (see {@link #setGutterPopupGroup(ActionGroup)},
   * {@link GutterIconRenderer#getPopupMenuActions()}, {@link TextAnnotationGutterProvider#getPopupActions(int, Editor)})
   */
  public static final DataKey<Integer> LOGICAL_LINE_AT_CURSOR = DataKey.create("EditorGutter.LOGICAL_LINE_AT_CURSOR");

  /**
   * The key to retrieve a editor gutter icon center position of a latest actionable click inside the gutter.
   * Available to gutter popup actions (see {@link #setGutterPopupGroup(ActionGroup)},
   * {@link GutterIconRenderer#getPopupMenuActions()}, {@link TextAnnotationGutterProvider#getPopupActions(int, Editor)})
   */
  public static final DataKey<Point> ICON_CENTER_POSITION = DataKey.create("EditorGutter.ICON_CENTER_POSITION");

  public abstract @Nullable FoldRegion findFoldingAnchorAt(int x, int y);

  public abstract @NotNull List<GutterMark> getGutterRenderers(int line);

  public abstract int getWhitespaceSeparatorOffset();

  public abstract void revalidateMarkup();

  public abstract int getLineMarkerAreaOffset();

  public abstract int getIconAreaOffset();

  public abstract int getLineMarkerFreePaintersAreaOffset();

  public abstract int getIconsAreaWidth();

  public abstract int getAnnotationsAreaOffset();

  public abstract int getAnnotationsAreaWidth();

  public abstract @Nullable Point getCenterPoint(GutterIconRenderer renderer);

  public abstract void setShowDefaultGutterPopup(boolean show);

  /** When set to false, makes {@link #closeAllAnnotations()} a no-op and hides the corresponding context menu action. */
  public abstract void setCanCloseAnnotations(boolean canCloseAnnotations);

  public abstract void setGutterPopupGroup(@Nullable ActionGroup group);

  public abstract boolean isPaintBackground();

  public abstract void setPaintBackground(boolean value);

  public abstract void setForceShowLeftFreePaintersArea(boolean value);

  public abstract void setForceShowRightFreePaintersArea(boolean value);

  public abstract void setLeftFreePaintersAreaWidth(int widthInPixels);

  public abstract void setRightFreePaintersAreaWidth(int widthInPixels);

  public abstract void setInitialIconAreaWidth(int width);

  public abstract @Nullable GutterMark getGutterRenderer(Point p);

  public abstract @Nullable Runnable setLoadingIconForCurrentGutterMark();

  @ApiStatus.Internal
  public abstract boolean isInsideMarkerArea(@NotNull MouseEvent e);
}
