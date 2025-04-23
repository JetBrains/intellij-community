// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.gitlab.icons;

import com.intellij.ui.IconManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * NOTE THIS FILE IS AUTO-GENERATED
 * DO NOT EDIT IT BY HAND, run "Generate icon classes" configuration instead
 */
public final class GitlabIcons {
  private static @NotNull Icon load(@NotNull String path, int cacheKey, int flags) {
    return IconManager.getInstance().loadRasterizedIcon(path, GitlabIcons.class.getClassLoader(), cacheKey, flags);
  }
  private static @NotNull Icon load(@NotNull String expUIPath, @NotNull String path, int cacheKey, int flags) {
    return IconManager.getInstance().loadRasterizedIcon(path, expUIPath, GitlabIcons.class.getClassLoader(), cacheKey, flags);
  }
  /** 16x16 */ public static final @NotNull Icon GitLabLogo = load("org/jetbrains/plugins/gitlab/gitLabLogo.svg", -65367349, 0);
  /** 13x13 */ public static final @NotNull Icon GitLabToolWindow = load("org/jetbrains/plugins/gitlab/expui/gitLabToolWindow.svg", "org/jetbrains/plugins/gitlab/gitLabToolWindow.svg", 858067490, 2);
  /** 16x16 */ public static final @NotNull Icon GitLabWarning = load("org/jetbrains/plugins/gitlab/gitLabWarning.svg", -1126375822, 2);
}
