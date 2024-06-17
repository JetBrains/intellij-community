// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package icons;

import com.intellij.ui.IconManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * NOTE THIS FILE IS AUTO-GENERATED
 * DO NOT EDIT IT BY HAND, run "Generate icon classes" configuration instead
 */
public final class GradleIcons {
  private static @NotNull Icon load(@NotNull String path, int cacheKey, int flags) {
    return IconManager.getInstance().loadRasterizedIcon(path, GradleIcons.class.getClassLoader(), cacheKey, flags);
  }
  private static @NotNull Icon load(@NotNull String path, @NotNull String expUIPath, int cacheKey, int flags) {
    return IconManager.getInstance().loadRasterizedIcon(path, expUIPath, GradleIcons.class.getClassLoader(), cacheKey, flags);
  }
  /** 16x16 */ public static final @NotNull Icon Gradle = load("icons/gradle.svg", "icons/expui/gradle.svg", 1785193254, 2);
  /** 16x16 */ public static final @NotNull Icon GradleFile = load("icons/gradleFile.svg", "icons/expui/gradle.svg", -392919783, 0);
  /** 16x16 */ public static final @NotNull Icon GradleLoadChanges = load("icons/gradleLoadChanges.svg", "icons/expui/gradleLoadChanges.svg", -927115520, 2);
  /** 16x16 */ public static final @NotNull Icon GradleNavigate = load("icons/gradleNavigate.svg", "icons/expui/gradleNavigate.svg", 2045411481, 2);
  /** 16x16 */ public static final @NotNull Icon GradleSubproject = load("icons/gradleSubproject.svg", -908281194, 2);
  /** 13x13 */ public static final @NotNull Icon ToolWindowGradle = load("icons/toolWindowGradle.svg", "icons/expui/gradle.svg", 661706798, 2);
}
