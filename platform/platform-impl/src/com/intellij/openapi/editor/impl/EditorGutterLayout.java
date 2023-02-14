// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.util.text.Strings;
import com.intellij.ui.ExperimentalUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * @author Konstantin Bulenkov
 */
public class EditorGutterLayout {
  static final String GAP_BETWEEN_AREAS = "Gap between areas";
  static final String LINE_NUMBERS_AREA = "Line numbers";
  static final String ADDITIONAL_LINE_NUMBERS_AREA = "Additional line numbers";
  static final String ANNOTATIONS_AREA = "Annotations";
  static final String LEFT_FREE_PAINTERS_AREA = "Left free painters";
  static final String ICONS_AREA = "Icons";
  static final String GAP_AFTER_ICONS_AREA = "Gap after icons";
  static final String RIGHT_FREE_PAINTERS_AREA = "Right free painters";
  static final String FOLDING_AREA = "Free painters";
  static final String VERTICAL_LINE_AREA = "Vertical line";
  private final EditorGutterComponentImpl myEditorGutter;
  private List<GutterArea> myExpLayout;
  private List<GutterArea> myClassicLayout;

  public EditorGutterLayout(EditorGutterComponentImpl editorGutter) {
    myEditorGutter = editorGutter;
  }

  public int getWidth() {
    return getLayout().stream().map(GutterArea::width).reduce(0, Integer::sum);
  }

  @Nullable
  public EditorMouseEventArea getEditorMouseAreaByOffset(int offset) {
    int off = 0;
    for (GutterArea area : getLayout()) {
      off += area.width();
      if (off >= offset) {
        return area.mouseEventAreaType;
      }
    }
    return null;
  }

  static class GutterArea {
    private final String id;
    private final Supplier<Integer> widthFunc;
    private EditorMouseEventArea mouseEventAreaType;
    private Supplier<Boolean> showIfFunc;

    GutterArea(String ID, Supplier<Integer> areaWidth) {
      id = ID;
      widthFunc = areaWidth;
      mouseEventAreaType = switch (ID) {
        case LINE_NUMBERS_AREA, ADDITIONAL_LINE_NUMBERS_AREA -> EditorMouseEventArea.LINE_NUMBERS_AREA;
        case ANNOTATIONS_AREA -> EditorMouseEventArea.ANNOTATIONS_AREA;
        case LEFT_FREE_PAINTERS_AREA, RIGHT_FREE_PAINTERS_AREA, ICONS_AREA -> EditorMouseEventArea.LINE_MARKERS_AREA;
        case GAP_AFTER_ICONS_AREA, FOLDING_AREA, VERTICAL_LINE_AREA -> EditorMouseEventArea.FOLDING_OUTLINE_AREA;
        default -> null;
      };
    }

    int width() {
      if (showIfFunc != null && !showIfFunc.get()) {
        return 0;
      }
      return widthFunc.get();
    }

    GutterArea as(EditorMouseEventArea type) {
      mouseEventAreaType = type;
      return this;
    }

    GutterArea showIf(Supplier<Boolean> showIf) {
      showIfFunc = showIf;
      return this;
    }
  }

  List<GutterArea> getLayout() {
    if (ExperimentalUI.isNewUI()) {
      return getExperimentalGutterLayout();
    }
    return getClassicGutterLayout();
  }

  private List<GutterArea> getClassicGutterLayout() {
    if (myClassicLayout == null) {
      myClassicLayout = createClassicLayout();
    }
    return myClassicLayout;
  }

  private List<GutterArea> createClassicLayout() {
    return List.of(
      areaGap()
        .as(EditorMouseEventArea.LINE_NUMBERS_AREA)
        .showIf(this::isLineNumbersShown),
      area(LINE_NUMBERS_AREA, () -> myEditorGutter.myLineNumberAreaWidth).showIf(this::isLineNumbersShown),
      area(ADDITIONAL_LINE_NUMBERS_AREA, () -> myEditorGutter.myAdditionalLineNumberAreaWidth),
      areaGap()
        .as(EditorMouseEventArea.LINE_MARKERS_AREA)
        .showIf(this::isLineNumbersShown),

      area(ANNOTATIONS_AREA, () -> myEditorGutter.myTextAnnotationGuttersSize),
      areaGap()
        .as(EditorMouseEventArea.LINE_MARKERS_AREA)
        .showIf(() -> myEditorGutter.isShowGapAfterAnnotations()),
      area(ANNOTATIONS_AREA, () -> myEditorGutter.myTextAnnotationExtraSize)
        .as(EditorMouseEventArea.LINE_MARKERS_AREA),

      area(LEFT_FREE_PAINTERS_AREA, myEditorGutter::getLeftFreePaintersAreaWidth),
      area(ICONS_AREA, myEditorGutter::getIconsAreaWidth).showIf(() -> myEditorGutter.isLineMarkersShown()),
      area(GAP_AFTER_ICONS_AREA, myEditorGutter::getGapAfterIconsArea).showIf(() -> myEditorGutter.isLineMarkersShown()),
      area(RIGHT_FREE_PAINTERS_AREA, myEditorGutter::getRightFreePaintersAreaWidth),

      area(FOLDING_AREA, myEditorGutter::getFoldingAreaWidth)
    );
  }

  private boolean isLineNumbersShown() {
    return myEditorGutter.isLineNumbersShown();
  }

  private List<GutterArea> createExperimentalLayout() {
    return List.of(
      area(ANNOTATIONS_AREA, () -> 4)
        .showIf(() -> myEditorGutter.myTextAnnotationGuttersSize == 0 && myEditorGutter.isLineMarkersShown()),
      areaGap()
        .as(EditorMouseEventArea.ANNOTATIONS_AREA)
        .showIf(() -> myEditorGutter.isShowGapAfterAnnotations() && myEditorGutter.isLineMarkersShown()),
      area(ANNOTATIONS_AREA, () -> myEditorGutter.myTextAnnotationGuttersSize)
        .showIf(() -> myEditorGutter.isLineMarkersShown()),

      //areaGap(1)
      //  .showIf(() -> myEditorGutter.getLeftFreePaintersAreaWidth() + myEditorGutter.getRightFreePaintersAreaWidth() > 0 && myEditorGutter.isLineMarkersShown()),
      area(LEFT_FREE_PAINTERS_AREA, myEditorGutter::getLeftFreePaintersAreaWidth)
        .showIf(() -> myEditorGutter.isLineMarkersShown()),
      area(RIGHT_FREE_PAINTERS_AREA, myEditorGutter::getRightFreePaintersAreaWidth)
        .showIf(() -> myEditorGutter.isLineMarkersShown()),
      areaGap(4)
        .as(EditorMouseEventArea.LINE_MARKERS_AREA)
        .showIf(() -> myEditorGutter.getLeftFreePaintersAreaWidth() + myEditorGutter.getRightFreePaintersAreaWidth() > 0 && myEditorGutter.isLineMarkersShown()),

      //areaGap(4).as(EditorMouseEventArea.LINE_NUMBERS_AREA).showIf(this::isLineNumbersShown),
      area(LINE_NUMBERS_AREA, () -> myEditorGutter.myLineNumberAreaWidth).showIf(this::isLineNumbersShown),
      areaGap(12).showIf(() -> myEditorGutter.isLineNumbersShown() && !myEditorGutter.isLineMarkersShown()),
      area(ADDITIONAL_LINE_NUMBERS_AREA, () -> myEditorGutter.myAdditionalLineNumberAreaWidth),
      area(ADDITIONAL_LINE_NUMBERS_AREA, () -> 4).showIf(() -> myEditorGutter.isLineNumbersShown() && myEditorGutter.isLineMarkersShown()),
      area(ANNOTATIONS_AREA, () -> myEditorGutter.myTextAnnotationExtraSize)
        .as(EditorMouseEventArea.LINE_MARKERS_AREA)
        .showIf(() -> myEditorGutter.isLineMarkersShown()),

      area(ICONS_AREA, myEditorGutter::getIconsAreaWidth).showIf(() -> myEditorGutter.isLineMarkersShown()),
      area(GAP_AFTER_ICONS_AREA, myEditorGutter::getGapAfterIconsArea),
      area(FOLDING_AREA, myEditorGutter::getFoldingAreaWidth),
      areaGap(3).showIf(() -> myEditorGutter.isLineMarkersShown())
      );
  }

  @NotNull
  private GutterArea areaGap() {
    return area(GAP_BETWEEN_AREAS, EditorGutterComponentImpl::getGapBetweenAreas);
  }

  @NotNull
  private GutterArea areaGap(int width) {
    return area(GAP_BETWEEN_AREAS, () -> width); //type something
  }

  private List<GutterArea> getExperimentalGutterLayout() {
    if (myExpLayout == null) {
      myExpLayout = createExperimentalLayout();
    }
    return myExpLayout;
  }

  private static GutterArea area(String id, Supplier<Integer> areaWidth) {
    return new GutterArea(id, areaWidth);
  }

  public int getAnnotationsAreaOffset() {
    return getOffset(ANNOTATIONS_AREA);
  }

  protected int getAreaWidth(String ID) {
    for (GutterArea area : getLayout()) {
      if (Strings.areSameInstance(area.id, ID)) {
        return area.width();
      }
    }
    return 0;
  }
  private int getOffset(String ID) {
    int offset = 0;
    for (GutterArea area : getLayout()) {
      if (Strings.areSameInstance(area.id, ID)) return offset;
      offset += area.width();
    }
    return -1;
  }

  //  getEditorMouseAreaByOffset
  public int getFoldingAreaOffset() {
    return getOffset(FOLDING_AREA);
  }
  public int getIconAreaOffset() {
    return getOffset(ICONS_AREA);
  }
  public int getLeftFreePaintersAreaOffset() {
    return getOffset(LEFT_FREE_PAINTERS_AREA);
  }
  public int getLineMarkerAreaOffset() {
    return getOffset(LEFT_FREE_PAINTERS_AREA);
  }
  public int getLineMarkerFreePaintersAreaOffset() {
    return getOffset(RIGHT_FREE_PAINTERS_AREA);
  }
  public int getLineNumberAreaOffset() {
    return getOffset(LINE_NUMBERS_AREA);
  }
  public int getVerticalLineX() {return getOffset(VERTICAL_LINE_AREA);}
}
