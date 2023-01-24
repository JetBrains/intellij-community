// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
public class VcsLogColorManagerImpl implements VcsLogColorManager {
  private static final Logger LOG = Logger.getInstance(VcsLogColorManagerImpl.class);

  private final @NotNull Map<String, Color> myPaths2Colors;
  private final @NotNull List<FilePath> myPaths;

  public VcsLogColorManagerImpl(@NotNull Set<? extends VirtualFile> roots, @NotNull List<Color> palette) {
    this(ContainerUtil.map(ContainerUtil.sorted(roots, Comparator.comparing(VirtualFile::getName)),
                           file -> VcsUtil.getFilePath(file)),
         palette
    );
  }

  public VcsLogColorManagerImpl(@NotNull Collection<? extends FilePath> paths, @NotNull List<Color> palette) {
    myPaths = new ArrayList<>(paths);
    myPaths2Colors = new HashMap<>();

    palette = palette.isEmpty() ? List.of(getDefaultRootColor()) : new ArrayList<>(palette);
    for (int i = 0; i < myPaths.size(); i++) {
      myPaths2Colors.put(myPaths.get(i).getPath(), getColor(i, myPaths.size(), palette));
    }
  }

  private static @NotNull Color getColor(int rootNumber, int rootsCount, @NotNull List<Color> palette) {
    Color color;
    int size = palette.size();
    if (rootNumber >= size) {
      double balance = ((double)(rootNumber / size)) / (rootsCount / size);
      Color mix = ColorUtil.mix(palette.get(rootNumber % size), palette.get((rootNumber + 1) % size), balance);
      int tones = (int)(Math.abs(balance - 0.5) * 2 * (rootsCount / size) + 1);
      color = new JBColor(ColorUtil.darker(mix, tones), ColorUtil.brighter(mix, 2 * tones));
    }
    else {
      color = palette.get(rootNumber);
    }
    return color;
  }

  @Override
  public @NotNull Color getPathColor(@NotNull FilePath path) {
    return getColor(path.getPath());
  }

  @Override
  public @NotNull Color getRootColor(@NotNull VirtualFile root) {
    return getColor(root.getPath());
  }

  private @NotNull Color getColor(@NotNull String path) {
    Color color = myPaths2Colors.get(path);
    if (color == null) {
      LOG.error("No color record for path " + path + ". All paths: " + myPaths2Colors);
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
}
