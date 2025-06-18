// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.vcs.impl.shared.ui.RepositoryColors;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Collection;

/**
 * Managers colors used for paths in the vcs log.
 *
 * @author Kirill Likhodedov
 * @see VcsLogColorManagerFactory
 */
public interface VcsLogColorManager {

  /**
   * Returns the color assigned to the given repository root.
   */
  default @NotNull Color getRootColor(@NotNull VirtualFile root) {
    return getRootColor(root, RepositoryColors.DEFAULT_COLOR_SPACE);
  }

  default @NotNull Color getRootColor(@NotNull VirtualFile root, @NotNull String colorSpace) {
    return getPathColor(VcsUtil.getFilePath(root), colorSpace);
  }

  /**
   * Returns the color assigned to the given file path.
   */
  default @NotNull Color getPathColor(@NotNull FilePath path) {
    return getPathColor(path, RepositoryColors.DEFAULT_COLOR_SPACE);
  }

  @NotNull
  Color getPathColor(@NotNull FilePath path, @NotNull String colorSpace);

  /**
   * Tells if there are several paths currently shown in the log.
   */
  default boolean hasMultiplePaths() {
    return getPaths().size() > 1;
  }

  /**
   * Returns paths managed by this manager.
   */
  @NotNull
  Collection<FilePath> getPaths();

  /**
   * Returns long name for this file path (to be shown in the tooltip, etc.).
   */
  default @NotNull @NlsSafe String getLongName(@NotNull FilePath path) {
    return path.getPresentableUrl();
  }
}
