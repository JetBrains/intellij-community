// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.icons;

import com.intellij.ui.IconManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * NOTE THIS FILE IS AUTO-GENERATED
 * DO NOT EDIT IT BY HAND, run "Generate icon classes" configuration instead
 */
@org.jetbrains.annotations.ApiStatus.Internal
public final class PlatformVcsImplIcons {
  private static @NotNull Icon load(@NotNull String path, int cacheKey, int flags) {
    return IconManager.getInstance().loadRasterizedIcon(path, PlatformVcsImplIcons.class.getClassLoader(), cacheKey, flags);
  }
  private static @NotNull Icon load(@NotNull String path, @NotNull String expUIPath, int cacheKey, int flags) {
    return IconManager.getInstance().loadRasterizedIcon(path, expUIPath, PlatformVcsImplIcons.class.getClassLoader(), cacheKey, flags);
  }
  /** 16x16 */ public static final @NotNull Icon Shelve = load("icons/Shelve.svg", "icons/new/stash.svg", -1645293825, 2);
  /** 16x16 */ public static final @NotNull Icon Stash = load("icons/Stash.svg", "icons/new/stash.svg", -451629034, 2);
  /** 16x16 */ public static final @NotNull Icon Vcs = load("icons/new/vcs.svg", 1023462254, 2);
}
