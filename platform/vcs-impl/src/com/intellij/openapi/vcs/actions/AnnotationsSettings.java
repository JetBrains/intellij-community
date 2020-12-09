// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.codeHighlighting.ColorGenerator;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class AnnotationsSettings {
  private static final int ANCHORS_COUNT = 5;
  private static final int COLORS_BETWEEN_ANCHORS = 4;
  private static final int SHUFFLE_STEP = 4;

  static final List<ColorKey> ANCHOR_COLOR_KEYS = createColorKeys(ANCHORS_COUNT);

  @NotNull
  private static List<ColorKey> createColorKeys(int count) {
    List<ColorKey> keys = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      keys.add(ColorKey.createColorKey("VCS_ANNOTATIONS_COLOR_" + (i + 1)));
    }
    return keys;
  }

  public static AnnotationsSettings getInstance() {
    return ApplicationManager.getApplication().getService(AnnotationsSettings.class);
  }

  @NotNull
  public List<Color> getAuthorsColors(@Nullable EditorColorsScheme scheme) {
    if (scheme == null) scheme = EditorColorsManager.getInstance().getGlobalScheme();
    List<Color> colors = getOrderedColors(scheme);

    List<Color> authorColors = new ArrayList<>();
    for (int i = 0; i < SHUFFLE_STEP; i++) {
      for (int k = 0; k <= colors.size() / SHUFFLE_STEP; k++) {
        int index = k * SHUFFLE_STEP + i;
        if (index < colors.size()) authorColors.add(colors.get(index));
      }
    }

    return authorColors;
  }

  @NotNull
  public List<Color> getOrderedColors(@Nullable EditorColorsScheme scheme) {
    if (scheme == null) scheme = EditorColorsManager.getInstance().getGlobalScheme();

    List<Color> anchorColors = new ArrayList<>();
    for (ColorKey key : ANCHOR_COLOR_KEYS) {
      ContainerUtil.addIfNotNull(anchorColors, scheme.getColor(key));
    }

    return ColorGenerator.generateLinearColorSequence(anchorColors, COLORS_BETWEEN_ANCHORS);
  }

  @NotNull
  List<Integer> getAnchorIndexes(@Nullable EditorColorsScheme scheme) {
    if (scheme == null) scheme = EditorColorsManager.getInstance().getGlobalScheme();

    List<Integer> result = new ArrayList<>(ANCHORS_COUNT);

    int count = 0;
    for (ColorKey key : ANCHOR_COLOR_KEYS) {
      if (scheme.getColor(key) != null) {
        result.add(count);
        count += COLORS_BETWEEN_ANCHORS + 1;
      }
      else {
        result.add(null);
      }
    }

    return result;
  }
}
