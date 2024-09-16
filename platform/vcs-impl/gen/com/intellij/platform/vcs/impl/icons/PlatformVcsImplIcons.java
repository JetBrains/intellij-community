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
  private static @NotNull Icon load(@NotNull String expUIPath, @NotNull String path, int cacheKey, int flags) {
    return IconManager.getInstance().loadRasterizedIcon(path, expUIPath, PlatformVcsImplIcons.class.getClassLoader(), cacheKey, flags);
  }
  /** 16x16 */ public static final @NotNull Icon AmendInline = load("icons/AmendInline.svg", 1594676608, 2);
  /** 16x16 */ public static final @NotNull Icon CommitInline = load("icons/CommitInline.svg", 747759737, 2);
  /** 16x16 */ public static final @NotNull Icon Shelve = load("icons/new/stash.svg", "icons/Shelve.svg", -1645293825, 2);
  /** 16x16 */ public static final @NotNull Icon Stash = load("icons/new/stash.svg", "icons/Stash.svg", -451629034, 2);
  /** 16x16 */ public static final @NotNull Icon Vcs = load("icons/new/vcs.svg", 1023462254, 2);
}
