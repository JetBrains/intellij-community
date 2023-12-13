// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * @author Kirill Likhodedov
 */
public class VcsLogColorManagerImpl implements VcsLogColorManager {
  private static final Logger LOG = Logger.getInstance(VcsLogColorManagerImpl.class);

  private final @NotNull Map<String, Map<String, Color>> myPath2Palette;
  private final @NotNull List<FilePath> myPaths;

  public VcsLogColorManagerImpl(
    @NotNull Collection<? extends FilePath> paths,
    @NotNull List<Color> defaultPalette,
    AdditionalColorSpace... additionalColorSpaces
  ) {
    myPaths = new ArrayList<>(paths);
    myPath2Palette = new HashMap<>();

    defaultPalette = defaultPalette.isEmpty() ? List.of(getDefaultRootColor()) : new ArrayList<>(defaultPalette);
    myPath2Palette.put(VcsLogColorManager.DEFAULT_COLOR_MODE, generateFromPalette(defaultPalette));

    for (AdditionalColorSpace colorSpace : additionalColorSpaces) {
      // do not allow to override default palette
      if (colorSpace.colorMode.equals(VcsLogColorManager.DEFAULT_COLOR_MODE)) continue;

      // allow additional palettes only the same size as the default
      if (colorSpace.palette.size() != defaultPalette.size()) continue;

      Map<String, Color> colors = generateFromPalette(new ArrayList<>(colorSpace.palette));
      myPath2Palette.put(colorSpace.colorMode, colors);
    }
  }

  private Map<String, Color> generateFromPalette(@NotNull List<Color> defaultPalette) {
    Map<String, Color> path2Palette = new HashMap<>();

    for (int i = 0; i < myPaths.size(); i++) {
      path2Palette.put(myPaths.get(i).getPath(), getColor(i, myPaths.size(), defaultPalette));
    }
    return path2Palette;
  }

  private static @NotNull Color getColor(int rootNumber, int rootsCount, @NotNull List<Color> palette) {
    Color color;
    int size = palette.size();
    if (rootNumber >= size) {
      double balance = ((double)(rootNumber / size)) / (rootsCount / size);
      Color mix = ColorUtil.mix(palette.get(rootNumber % size), palette.get((rootNumber + 1) % size), balance);
      int tones = (int)(Math.abs(balance - 0.5) * 2 * (rootsCount / size) + 1);
      if (mix instanceof JBColor) {
        color = JBColor.lazy(() -> new JBColor(ColorUtil.darker(mix, tones), ColorUtil.brighter(mix, 2 * tones)));
      }
      else {
        color = new JBColor(ColorUtil.darker(mix, tones), ColorUtil.brighter(mix, 2 * tones));
      }
    }
    else {
      color = palette.get(rootNumber);
    }
    return color;
  }

  @Override
  public @NotNull Color getPathColor(@NotNull FilePath path, @NotNull String colorMode) {
    return getColor(path.getPath(), colorMode);
  }

  @Override
  public @NotNull Color getRootColor(@NotNull VirtualFile root, @NotNull String colorMode) {
    return getColor(root.getPath(), colorMode);
  }

  private @NotNull Color getColor(@NotNull String path, @NotNull String colorMode) {
    Map<String, Color> paletteToColor = myPath2Palette.getOrDefault(colorMode, myPath2Palette.get(DEFAULT_COLOR_MODE));
    Color color = paletteToColor.get(path);
    if (color == null) {
      LOG.error("No color record for path " + path + ". All paths: " + paletteToColor);
      color = getDefaultRootColor();
    }
    return color;
  }

  private static @NotNull Color getDefaultRootColor() {
    return UIUtil.getTableBackground();
  }

  @Override
  public @NotNull Collection<FilePath> getPaths() {
    return myPaths;
  }

  public static class AdditionalColorSpace {
    private final String colorMode;
    private final List<Color> palette;

    AdditionalColorSpace(@NotNull String colorMode, @NotNull List<Color> palette) {
      this.colorMode = colorMode;
      this.palette = palette;
    }
  }
}
