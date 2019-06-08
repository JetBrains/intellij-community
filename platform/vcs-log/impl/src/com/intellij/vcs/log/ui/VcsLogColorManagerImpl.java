// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.containers.ContainerUtil;
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

  private static final Color[] ROOT_COLORS =
    {JBColor.RED, JBColor.GREEN, JBColor.BLUE, JBColor.ORANGE, JBColor.CYAN, JBColor.YELLOW, JBColor.MAGENTA, JBColor.PINK};

  @NotNull private final Map<VirtualFile, Color> myRoots2Colors;

  public VcsLogColorManagerImpl(@NotNull Collection<VirtualFile> roots) {
    List<VirtualFile> sortedRoots = ContainerUtil.sorted(roots, Comparator.comparing(VirtualFile::getName));

    myRoots2Colors = new HashMap<>();
    for (int i = 0; i < sortedRoots.size(); i++) {
      myRoots2Colors.put(sortedRoots.get(i), getColor(i, sortedRoots.size()));
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
    return new JBColor(() -> ColorUtil.mix(baseRootColor, UIUtil.getTableBackground(), 0.75));
  }

  @Override
  public boolean isMultipleRoots() {
    return myRoots2Colors.size() > 1;
  }

  @NotNull
  @Override
  public Color getRootColor(@NotNull VirtualFile root) {
    Color color = myRoots2Colors.get(root);
    if (color == null) {
      LOG.error("No color record for root " + root + ". All roots: " + myRoots2Colors);
      color = getDefaultRootColor();
    }
    return color;
  }

  private static Color getDefaultRootColor() {
    return UIUtil.getTableBackground();
  }
}
