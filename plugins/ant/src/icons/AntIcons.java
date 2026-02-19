// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package icons;

import com.intellij.ui.IconManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * NOTE THIS FILE IS AUTO-GENERATED
 * DO NOT EDIT IT BY HAND, run "Generate icon classes" configuration instead
 */
public final class AntIcons {
  private static @NotNull Icon load(@NotNull String path, int cacheKey, int flags) {
    return IconManager.getInstance().loadRasterizedIcon(path, AntIcons.class.getClassLoader(), cacheKey, flags);
  }
  private static @NotNull Icon load(@NotNull String expUIPath, @NotNull String path, int cacheKey, int flags) {
    return IconManager.getInstance().loadRasterizedIcon(path, expUIPath, AntIcons.class.getClassLoader(), cacheKey, flags);
  }
  /** 16x16 */ public static final @NotNull Icon AntBuildXml = load("icons/expui/ant.svg", "icons/AntBuildXml.svg", 1994930586, 2);
  /** 16x16 */ public static final @NotNull Icon AntTask = load("icons/antTask.svg", 1295125394, 0);
  /** 16x16 */ public static final @NotNull Icon Build = load("icons/expui/ant.svg", "icons/build.svg", 2113580401, 0);
  /** 16x16 */ public static final @NotNull Icon LogDebug = load("icons/logDebug.svg", 1226967148, 0);
  /** 16x16 */ public static final @NotNull Icon LogVerbose = load("icons/logVerbose.svg", -1085984365, 0);
  /** 16x16 */ public static final @NotNull Icon MetaTarget = load("icons/metaTarget.svg", 1565197878, 0);
  /** 16x16 */ public static final @NotNull Icon Verbose = load("icons/verbose.svg", -124247784, 2);
}
