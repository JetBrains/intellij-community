// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * @author Kirill Likhodedov
 */
public final class VcsLogColorManagerImpl implements VcsLogColorManager {
  private static final Logger LOG = Logger.getInstance(VcsLogColorManagerImpl.class);

  private static final Color[] ROOT_COLORS =
    {JBColor.RED, JBColor.GREEN, JBColor.BLUE, JBColor.ORANGE, JBColor.CYAN, JBColor.YELLOW, JBColor.MAGENTA, JBColor.PINK};

  @NotNull private final Map<String, Color> myPaths2Colors;
  @NotNull private final List<FilePath> myPaths;

  public VcsLogColorManagerImpl(@NotNull Set<VirtualFile> roots) {
    this(ContainerUtil.map(ContainerUtil.sorted(roots, Comparator.comparing(VirtualFile::getName)),
                           file -> VcsUtil.getFilePath(file)));
  }

  public VcsLogColorManagerImpl(@NotNull Collection<FilePath> paths) {
    myPaths = new ArrayList<>(paths);
    myPaths2Colors = new HashMap<>();
    for (int i = 0; i < myPaths.size(); i++) {
      myPaths2Colors.put(myPaths.get(i).getPath(), getColor(i, myPaths.size()));
    }
  }

  @NotNull
  private static Color getColor(int rootNumber, int rootsCount) {
    Color color;
    if (rootNumber >= ROOT_COLORS.length) {
      double balance = ((double)(rootNumber / ROOT_COLORS.length)) / (rootsCount / ROOT_COLORS.length);
      Color mix = ColorUtil.mix(ROOT_COLORS[rootNumber % ROOT_COLORS.length], ROOT_COLORS[(rootNumber + 1) % ROOT_COLORS.length], balance);
      int tones = (int)(Math.abs(balance - 0.5) * 2 * (rootsCount / ROOT_COLORS.length) + 1);
      color = new JBColor(ColorUtil.darker(mix, tones), ColorUtil.brighter(mix, 2 * tones));
    }
    else {
      color = ROOT_COLORS[rootNumber];
    }
    return color;
  }

  @NotNull
  public static JBColor getBackgroundColor(@NotNull final Color baseRootColor) {
    return JBColor.lazy(() -> ColorUtil.mix(baseRootColor, UIUtil.getTableBackground(), 0.75));
  }

  @NotNull
  @Override
  public Color getPathColor(@NotNull FilePath path) {
    return getColor(path.getPath());
  }

  @NotNull
  @Override
  public Color getRootColor(@NotNull VirtualFile root) {
    return getColor(root.getPath());
  }

  @NotNull
  private Color getColor(@NotNull String path) {
    Color color = myPaths2Colors.get(path);
    if (color == null) {
      LOG.error("No color record for path " + path + ". All paths: " + myPaths2Colors);
      color = getDefaultRootColor();
    }
    return color;
  }

  @NotNull
  private static Color getDefaultRootColor() {
    return UIUtil.getTableBackground();
  }

  @NotNull
  @Override
  public Collection<FilePath> getPaths() {
    return myPaths;
  }
}
