// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training;

import com.intellij.ui.IconManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * NOTE THIS FILE IS AUTO-GENERATED
 * DO NOT EDIT IT BY HAND, run "Generate icon classes" configuration instead
 */
public final class FeaturesTrainerIcons {
  private static @NotNull Icon load(@NotNull String path, int cacheKey, int flags) {
    return IconManager.getInstance().loadRasterizedIcon(path, FeaturesTrainerIcons.class.getClassLoader(), cacheKey, flags);
  }
  private static @NotNull Icon load(@NotNull String expUIPath, @NotNull String path, int cacheKey, int flags) {
    return IconManager.getInstance().loadRasterizedIcon(path, expUIPath, FeaturesTrainerIcons.class.getClassLoader(), cacheKey, flags);
  }
  /** 16x16 */ public static final @NotNull Icon Checkmark = load("img/expui/checkmark.svg", "img/checkmark.svg", 1210931315, 2);
  /** 16x16 */ public static final @NotNull Icon FeatureTrainer = load("img/expui/toolwindow/learn.svg", "img/featureTrainer.svg", 1806467053, 2);
  /** 13x13 */ public static final @NotNull Icon FeatureTrainerToolWindow = load("img/expui/toolwindow/learn.svg", "img/featureTrainerToolWindow.svg", -68627899, 2);
  /** 16x16 */ public static final @NotNull Icon PluginIcon = load("img/pluginIcon.svg", -1574300806, 0);
  /** 16x16 */ public static final @NotNull Icon ResetLesson = load("img/expui/resetLesson.svg", "img/resetLesson.svg", 1838614018, 2);
}
