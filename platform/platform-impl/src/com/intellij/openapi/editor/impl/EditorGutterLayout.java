// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.ide.actions.DistractionFreeModeController;
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
public final class EditorGutterLayout {
  private static final String GAP_BETWEEN_AREAS = "Gap between areas";
  private static final String LINE_NUMBERS_AREA = "Line numbers";
  private static final String ADDITIONAL_LINE_NUMBERS_AREA = "Additional line numbers";
  private static final String ANNOTATIONS_AREA = "Annotations";
  // this zone is shown to the left of the line numbers in the new UI
  private static final String EXTRA_LEFT_FREE_PAINTERS_AREA = "Extra Left free painters";
  private static final String LEFT_FREE_PAINTERS_AREA = "Left free painters";
  private static final String ICONS_AREA = "Icons";
  private static final String GAP_AFTER_ICONS_AREA = "Gap after icons";
  private static final String RIGHT_FREE_PAINTERS_AREA = "Right free painters";
  private static final String FOLDING_AREA = "Free painters";
  private static final String VERTICAL_LINE_AREA = "Vertical line";
  private final EditorGutterComponentImpl myEditorGutter;
  private List<GutterArea> myNewUILayout;
  private List<GutterArea> myNewUIDFMLayout;
  private List<GutterArea> myClassicLayout;

  EditorGutterLayout(@NotNull EditorGutterComponentImpl editorGutter) {
    myEditorGutter = editorGutter;
  }

  public int getWidth() {
    return getLayout().stream().map(GutterArea::width).reduce(0, Integer::sum);
  }

  public @Nullable EditorMouseEventArea getEditorMouseAreaByOffset(int offset) {
    int off = 0;
    for (GutterArea area : getLayout()) {
      off += area.width();
      if (off >= offset) {
        return area.mouseEventAreaType;
      }
    }
    return null;
  }

  static final class GutterArea {
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
        case EXTRA_LEFT_FREE_PAINTERS_AREA, LEFT_FREE_PAINTERS_AREA, RIGHT_FREE_PAINTERS_AREA, ICONS_AREA ->
          EditorMouseEventArea.LINE_MARKERS_AREA;
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
      if (DistractionFreeModeController.isDistractionFreeModeEnabled()) {
        return getNewUIDFMLayout();
      }
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

      area(ANNOTATIONS_AREA, () -> myEditorGutter.myTextAnnotationGuttersSize)
        .as(EditorMouseEventArea.ANNOTATIONS_AREA)
        .showIf(() -> myEditorGutter.myTextAnnotationGuttersSize != 0),
      areaGap()
        .as(EditorMouseEventArea.ANNOTATIONS_AREA)
        .showIf(() -> myEditorGutter.isShowGapAfterAnnotations()),
      area(ANNOTATIONS_AREA, () -> myEditorGutter.myTextAnnotationExtraSize)
        .as(EditorMouseEventArea.LINE_MARKERS_AREA) // Distraction-free mode is extended using this area, see IDEA-320495
        .showIf(() -> myEditorGutter.myTextAnnotationExtraSize != 0),

      area(LEFT_FREE_PAINTERS_AREA, myEditorGutter::getLeftFreePaintersAreaWidth).showIf(myEditorGutter::isLineMarkersShown),
      area(ICONS_AREA, myEditorGutter::getIconsAreaWidth).showIf(myEditorGutter::isLineMarkersShown),
      area(GAP_AFTER_ICONS_AREA, myEditorGutter::getGapAfterIconsArea).showIf(myEditorGutter::isLineMarkersShown),
      area(RIGHT_FREE_PAINTERS_AREA, myEditorGutter::getRightFreePaintersAreaWidth).showIf(myEditorGutter::isLineMarkersShown),

      area(FOLDING_AREA, myEditorGutter::getFoldingAreaWidth)
    );
  }

  private boolean isLineNumbersShown() {
    return myEditorGutter.isLineNumbersShown();
  }

  private List<GutterArea> createNewUILayout() {
    return List.of(
      area(ANNOTATIONS_AREA, () -> 4)
        .as(EditorMouseEventArea.ANNOTATIONS_AREA)
        .showIf(() -> myEditorGutter.myTextAnnotationGuttersSize == 0 && myEditorGutter.isLineMarkersShown()),
      areaGap()
        .as(EditorMouseEventArea.ANNOTATIONS_AREA)
        .showIf(() -> myEditorGutter.isShowGapAfterAnnotations() && myEditorGutter.isLineMarkersShown()),
      area(ANNOTATIONS_AREA, () -> myEditorGutter.myTextAnnotationGuttersSize)
        .as(EditorMouseEventArea.ANNOTATIONS_AREA)
        .showIf(() -> myEditorGutter.myTextAnnotationGuttersSize != 0),

      //areaGap(1)
      //  .showIf(() -> myEditorGutter.getLeftFreePaintersAreaWidth() + myEditorGutter.getRightFreePaintersAreaWidth() > 0 && myEditorGutter.isLineMarkersShown()),
      area(EXTRA_LEFT_FREE_PAINTERS_AREA, myEditorGutter::getExtraLeftFreePaintersAreaWidth)
        .showIf(() -> myEditorGutter.isLineMarkersShown()),
      areaGap(4)
        .as(EditorMouseEventArea.LINE_MARKERS_AREA)
        .showIf(() -> myEditorGutter.getExtraLeftFreePaintersAreaWidth() > 0 && myEditorGutter.isLineMarkersShown()),

      //areaGap(4).as(EditorMouseEventArea.LINE_NUMBERS_AREA).showIf(this::isLineNumbersShown),
      area(LINE_NUMBERS_AREA, () -> myEditorGutter.myLineNumberAreaWidth).showIf(this::isLineNumbersShown),
      areaGap(12).showIf(() -> myEditorGutter.isLineNumbersShown() && !myEditorGutter.isLineMarkersShown()),
      area(ADDITIONAL_LINE_NUMBERS_AREA, () -> myEditorGutter.myAdditionalLineNumberAreaWidth),
      area(ADDITIONAL_LINE_NUMBERS_AREA, () -> 4).showIf(() -> myEditorGutter.isLineNumbersShown() && myEditorGutter.isLineMarkersShown()),
      area(ANNOTATIONS_AREA, () -> myEditorGutter.myTextAnnotationExtraSize)
        .as(EditorMouseEventArea.LINE_MARKERS_AREA)
        .showIf(() -> myEditorGutter.myTextAnnotationExtraSize != 0),

      area(LEFT_FREE_PAINTERS_AREA, myEditorGutter::getLeftFreePaintersAreaWidth).showIf(myEditorGutter::isLineMarkersShown),
      area(ICONS_AREA, myEditorGutter::getIconsAreaWidth).showIf(myEditorGutter::isLineMarkersShown),
      area(GAP_AFTER_ICONS_AREA, myEditorGutter::getGapAfterIconsArea).showIf(myEditorGutter::isLineMarkersShown),
      area(RIGHT_FREE_PAINTERS_AREA, myEditorGutter::getRightFreePaintersAreaWidth).showIf(myEditorGutter::isLineMarkersShown),

      area(FOLDING_AREA, myEditorGutter::getFoldingAreaWidth),
      areaGap(3).showIf(() -> myEditorGutter.isLineMarkersShown())
      );
  }

  private List<GutterArea> createNewUIDFMLayout() {
    return List.of(
      area(ANNOTATIONS_AREA, () -> 4)
        .as(EditorMouseEventArea.ANNOTATIONS_AREA)
        .showIf(() -> myEditorGutter.myTextAnnotationGuttersSize == 0 && myEditorGutter.isLineMarkersShown()),
      areaGap()
        .as(EditorMouseEventArea.ANNOTATIONS_AREA)
        .showIf(() -> myEditorGutter.isShowGapAfterAnnotations() && myEditorGutter.isLineMarkersShown()),
      area(ANNOTATIONS_AREA, () -> myEditorGutter.myTextAnnotationGuttersSize)
        .as(EditorMouseEventArea.ANNOTATIONS_AREA)
        .showIf(() -> myEditorGutter.myTextAnnotationGuttersSize != 0),
      area(ANNOTATIONS_AREA, () -> myEditorGutter.myTextAnnotationExtraSize)
        .as(EditorMouseEventArea.LINE_MARKERS_AREA)
        .showIf(() -> myEditorGutter.myTextAnnotationExtraSize != 0),

      area(LINE_NUMBERS_AREA, () -> myEditorGutter.myLineNumberAreaWidth).showIf(this::isLineNumbersShown),
      areaGap(12).showIf(() -> myEditorGutter.isLineNumbersShown() && !myEditorGutter.isLineMarkersShown()),
      area(ADDITIONAL_LINE_NUMBERS_AREA, () -> myEditorGutter.myAdditionalLineNumberAreaWidth),
      area(ADDITIONAL_LINE_NUMBERS_AREA, () -> 4).showIf(() -> myEditorGutter.isLineNumbersShown() && myEditorGutter.isLineMarkersShown()),
      area(EXTRA_LEFT_FREE_PAINTERS_AREA, myEditorGutter::getExtraLeftFreePaintersAreaWidth)
        .showIf(() -> myEditorGutter.isLineMarkersShown()),
      areaGap(4)
        .as(EditorMouseEventArea.LINE_MARKERS_AREA)
        .showIf(() -> myEditorGutter.getExtraLeftFreePaintersAreaWidth() > 0 && myEditorGutter.isLineMarkersShown()),

      area(LEFT_FREE_PAINTERS_AREA, myEditorGutter::getLeftFreePaintersAreaWidth).showIf(myEditorGutter::isLineMarkersShown),
      area(ICONS_AREA, myEditorGutter::getIconsAreaWidth).showIf(myEditorGutter::isLineMarkersShown),
      area(GAP_AFTER_ICONS_AREA, myEditorGutter::getGapAfterIconsArea).showIf(myEditorGutter::isLineMarkersShown),
      area(RIGHT_FREE_PAINTERS_AREA, myEditorGutter::getRightFreePaintersAreaWidth).showIf(myEditorGutter::isLineMarkersShown),

      area(FOLDING_AREA, myEditorGutter::getFoldingAreaWidth),
      areaGap(3).showIf(() -> myEditorGutter.isLineMarkersShown())
    );
  }

  private static @NotNull GutterArea areaGap() {
    return area(GAP_BETWEEN_AREAS, EditorGutterComponentImpl::getGapBetweenAreas);
  }

  private static @NotNull GutterArea areaGap(int width) {
    return area(GAP_BETWEEN_AREAS, () -> width); //type something
  }

  private List<GutterArea> getExperimentalGutterLayout() {
    if (myNewUILayout == null) {
      myNewUILayout = createNewUILayout();
    }
    return myNewUILayout;
  }

  private List<GutterArea> getNewUIDFMLayout() {
    if (myNewUIDFMLayout == null) {
      myNewUIDFMLayout = createNewUIDFMLayout();
    }
    return myNewUIDFMLayout;
  }

  private static GutterArea area(String id, Supplier<Integer> areaWidth) {
    return new GutterArea(id, areaWidth);
  }

  public int getAnnotationsAreaOffset() {
    return getOffset(ANNOTATIONS_AREA);
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
  int getFoldingAreaOffset() {
    return getOffset(FOLDING_AREA);
  }
  int getIconAreaOffset() {
    return getOffset(ICONS_AREA);
  }
  int getExtraLeftFreePaintersAreaOffset() {
    return getOffset(EXTRA_LEFT_FREE_PAINTERS_AREA);
  }
  public int getLeftFreePaintersAreaOffset() {
    return getOffset(LEFT_FREE_PAINTERS_AREA);
  }
  int getLineMarkerAreaOffset() {
    return getOffset(LEFT_FREE_PAINTERS_AREA);
  }
  int getLineMarkerFreePaintersAreaOffset() {
    return getOffset(RIGHT_FREE_PAINTERS_AREA);
  }
  int getLineNumberAreaOffset() {
    return getOffset(LINE_NUMBERS_AREA);
  }
  public int getVerticalLineX() {return getOffset(VERTICAL_LINE_AREA);}
}
