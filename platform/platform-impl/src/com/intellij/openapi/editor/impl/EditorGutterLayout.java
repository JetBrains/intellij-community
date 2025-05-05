// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.ide.actions.DistractionFreeModeController;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.util.text.Strings;
import com.intellij.ui.ExperimentalUI;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static com.intellij.openapi.editor.impl.EditorGutterComponentImpl.getGapBetweenAreas;

/**
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
public final class EditorGutterLayout {
  private static final String GAP_BETWEEN_AREAS = "Gap between areas";
  private static final String LINE_NUMBERS_AREA = "Line numbers";
  private static final String ADDITIONAL_LINE_NUMBERS_AREA = "Additional line numbers";
  private static final String ANNOTATIONS_AREA = "Annotations";
  private static final String LEFT_FREE_PAINTERS_AREA = "Left free painters";
  private static final String ICONS_AREA = "Icons";
  private static final String GAP_AFTER_ICONS_AREA = "Gap after icons";
  private static final String RIGHT_FREE_PAINTERS_AREA = "Right free painters";
  // this zone is shown last in the new UI
  private static final String EXTRA_RIGHT_FREE_PAINTERS_AREA = "Extra Right free painters";
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
        case LEFT_FREE_PAINTERS_AREA, RIGHT_FREE_PAINTERS_AREA, EXTRA_RIGHT_FREE_PAINTERS_AREA, GAP_AFTER_ICONS_AREA, ICONS_AREA ->
          EditorMouseEventArea.LINE_MARKERS_AREA;
        case FOLDING_AREA, VERTICAL_LINE_AREA -> EditorMouseEventArea.FOLDING_OUTLINE_AREA;
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

    @Override
    public String toString() {
      return id + "=" + width();
    }
  }

  List<GutterArea> getLayout() {
    if (ExperimentalUI.isNewUI()) {
      if (DistractionFreeModeController.isDistractionFreeModeEnabled()) {
        return getNewUIDFMLayout();
      }
      return getNewUiLayout();
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
    List<GutterArea> lineNumbersAreas = List.of(
      areaGap()
        .as(EditorMouseEventArea.LINE_NUMBERS_AREA)
        .showIf(this::isLineNumbersShown),
      area(LINE_NUMBERS_AREA, () -> myEditorGutter.myLineNumberAreaWidth)
        .showIf(this::isLineNumbersShown),
      area(ADDITIONAL_LINE_NUMBERS_AREA, () -> myEditorGutter.myAdditionalLineNumberAreaWidth)
        .showIf(this::isLineNumbersShown),
      areaGap() // Note: ADDITIONAL_LINE_NUMBERS_AREA rendering depends on this gap
        .as(EditorMouseEventArea.LINE_MARKERS_AREA)
        .showIf(this::isLineNumbersShown)
    );

    List<GutterArea> annotationAreas = List.of(
      area(ANNOTATIONS_AREA, () -> myEditorGutter.myTextAnnotationGuttersSize)
        .showIf(() -> myEditorGutter.myTextAnnotationGuttersSize != 0),
      areaGap()
        .as(EditorMouseEventArea.ANNOTATIONS_AREA)
        .showIf(() -> myEditorGutter.isShowGapAfterAnnotations())
    );

    // Distraction-free mode is extended using this area, see IDEA-320495
    List<GutterArea> dfmMarginArea = List.of(
      area(ANNOTATIONS_AREA, () -> myEditorGutter.myTextAnnotationExtraSize)
        .as(EditorMouseEventArea.LINE_MARKERS_AREA)
        .showIf(() -> myEditorGutter.myTextAnnotationExtraSize != 0)
    );

    List<GutterArea> iconRelatedAreas = List.of(
      area(LEFT_FREE_PAINTERS_AREA, myEditorGutter::getLeftFreePaintersAreaWidth).showIf(myEditorGutter::isLineMarkersShown),
      area(ICONS_AREA, myEditorGutter::getIconsAreaWidth).showIf(myEditorGutter::isLineMarkersShown),
      area(GAP_AFTER_ICONS_AREA, myEditorGutter::getGapAfterIconsArea).showIf(myEditorGutter::isLineMarkersShown)
    );

    List<GutterArea> rightEdgeAreas = List.of(
      area(RIGHT_FREE_PAINTERS_AREA, myEditorGutter::getRightFreePaintersAreaWidth).showIf(myEditorGutter::isLineMarkersShown),
      area(FOLDING_AREA, myEditorGutter::getFoldingAreaWidth)
    );

    List<GutterArea> layout;

    if (isLineNumbersAfterIcons()) {
      layout = new ArrayList<>();
      layout.addAll(annotationAreas);
      layout.addAll(dfmMarginArea);
      layout.addAll(iconRelatedAreas);
      layout.addAll(lineNumbersAreas);
      layout.addAll(rightEdgeAreas);
    } else {
      layout = new ArrayList<>();
      layout.addAll(lineNumbersAreas);
      layout.addAll(annotationAreas);
      layout.addAll(dfmMarginArea);
      layout.addAll(iconRelatedAreas);
      layout.addAll(rightEdgeAreas);
    }

    return layout;
  }

  private boolean isLineNumbersShown() {
    return myEditorGutter.isLineNumbersShown();
  }

  private boolean isLineNumbersAfterIcons() {
    return myEditorGutter.isLineNumbersAfterIcons();
  }

  private List<GutterArea> createNewUILayout(boolean isDistractionFreeMode) {
    List<GutterArea> annotationAreas = List.of(
      areaGap()
        .as(EditorMouseEventArea.ANNOTATIONS_AREA)
        .showIf(() -> isLineNumbersAfterIcons()),
      area(ANNOTATIONS_AREA, EditorGutterComponentImpl.EMPTY_ANNOTATION_AREA_WIDTH::get)
        .showIf(() -> myEditorGutter.myTextAnnotationGuttersSize == 0 && myEditorGutter.isLineMarkersShown()),
      areaGap()
        .as(EditorMouseEventArea.ANNOTATIONS_AREA)
        .showIf(() -> myEditorGutter.isShowGapAfterAnnotations() && myEditorGutter.isLineMarkersShown()),
      area(ANNOTATIONS_AREA, () -> myEditorGutter.myTextAnnotationGuttersSize)
        .showIf(() -> myEditorGutter.myTextAnnotationGuttersSize != 0)
    );

    List<GutterArea> lineNumbersAreas = List.of(
      areaGap(gapBeforeLineMarkersWidth())
        .as(EditorMouseEventArea.LINE_NUMBERS_AREA)
        .showIf(this::isLineNumbersShown),
      area(LINE_NUMBERS_AREA, () -> myEditorGutter.myLineNumberAreaWidth)
        .showIf(this::isLineNumbersShown),
      areaGap(12)
        .showIf(() -> isLineNumbersShown() && !myEditorGutter.isLineMarkersShown()),
      area(ADDITIONAL_LINE_NUMBERS_AREA, () -> myEditorGutter.myAdditionalLineNumberAreaWidth)
        .showIf(this::isLineNumbersShown),
      area(ADDITIONAL_LINE_NUMBERS_AREA, () -> {
        // Note: ADDITIONAL_LINE_NUMBERS_AREA rendering depends on this gap
        return Math.max(myEditorGutter.isLineMarkersShown() ? EditorGutterComponentImpl.GAP_AFTER_LINE_NUMBERS_WIDTH.get() : 0,
                        myEditorGutter.myAdditionalLineNumberAreaWidth > 0 ? getGapBetweenAreas() : 0);
      })
        .showIf(() -> isLineNumbersShown())
    );

    List<GutterArea> dfmMarginArea = List.of(
      area(ANNOTATIONS_AREA, () -> myEditorGutter.myTextAnnotationExtraSize)
        .as(EditorMouseEventArea.LINE_MARKERS_AREA)
        .showIf(() -> myEditorGutter.myTextAnnotationExtraSize != 0)
    );

    List<GutterArea> iconRelatedAreas = List.of(
      area(LEFT_FREE_PAINTERS_AREA, myEditorGutter::getLeftFreePaintersAreaWidth).showIf(myEditorGutter::isLineMarkersShown),
      area(ICONS_AREA, myEditorGutter::getIconsAreaWidth).showIf(myEditorGutter::isLineMarkersShown),
      area(GAP_AFTER_ICONS_AREA, myEditorGutter::getGapAfterIconsArea).showIf(
        () -> myEditorGutter.isLineMarkersShown() && !isLineNumbersAfterIcons())
    );

    List<GutterArea> rightEdgeAreas = List.of(
      area(RIGHT_FREE_PAINTERS_AREA, myEditorGutter::getRightFreePaintersAreaWidth).showIf(myEditorGutter::isLineMarkersShown),
      area(FOLDING_AREA, myEditorGutter::getFoldingAreaWidth)
    );

    List<GutterArea> rightEdgeAreasForLineNumbersAfterIcons = List.of(
      area(FOLDING_AREA, myEditorGutter::getFoldingAreaWidthForLineNumbersAfterIcons)
    );

    List<GutterArea> extraRightFreePainters = List.of(
      area(EXTRA_RIGHT_FREE_PAINTERS_AREA, myEditorGutter::getExtraRightFreePaintersAreaWidth)
        .showIf(() -> myEditorGutter.isLineMarkersShown()),
      areaGap(1).showIf(() -> myEditorGutter.isLineMarkersShown())
    );

    List<GutterArea> layout = new ArrayList<>();
    if (isDistractionFreeMode) {
      layout.addAll(annotationAreas);
      layout.addAll(dfmMarginArea);
      layout.addAll(lineNumbersAreas);
      layout.addAll(iconRelatedAreas);
      layout.addAll(rightEdgeAreas);
      layout.addAll(extraRightFreePainters);
    }
    else if (isLineNumbersAfterIcons()) {
      layout.addAll(annotationAreas);
      layout.addAll(dfmMarginArea);
      layout.addAll(iconRelatedAreas);
      layout.addAll(lineNumbersAreas);
      layout.addAll(rightEdgeAreasForLineNumbersAfterIcons);
      layout.addAll(extraRightFreePainters);
    }
    else {
      layout.addAll(annotationAreas);
      layout.addAll(lineNumbersAreas);
      layout.addAll(dfmMarginArea);
      layout.addAll(iconRelatedAreas);
      layout.addAll(rightEdgeAreas);
      layout.addAll(extraRightFreePainters);
    }
    return layout;
  }

  private static int gapBeforeLineMarkersWidth() {
    return JBUI.CurrentTheme.Editor.Gutter.gapAfterVcsMarkersWidth();
  }

  private static @NotNull GutterArea areaGap() {
    return area(GAP_BETWEEN_AREAS, EditorGutterComponentImpl::getGapBetweenAreas);
  }

  private static @NotNull GutterArea areaGap(int width) {
    return area(GAP_BETWEEN_AREAS, () -> JBUI.scale(width)); //type something
  }

  private List<GutterArea> getNewUiLayout() {
    if (myNewUILayout == null) {
      myNewUILayout = createNewUILayout(false);
    }
    return myNewUILayout;
  }

  private List<GutterArea> getNewUIDFMLayout() {
    if (myNewUIDFMLayout == null) {
      myNewUIDFMLayout = createNewUILayout(true);
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

  int getExtraRightFreePaintersAreaOffset() {
    return getOffset(EXTRA_RIGHT_FREE_PAINTERS_AREA);
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

  public int getVerticalLineX() { return getOffset(VERTICAL_LINE_AREA); }

  public static int getInitialGutterWidth() {
    return EditorGutterComponentImpl.START_ICON_AREA_WIDTH.get();
  }
}
