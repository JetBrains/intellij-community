// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package icons;

import com.intellij.ui.IconManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * NOTE THIS FILE IS AUTO-GENERATED
 * DO NOT EDIT IT BY HAND, run "Generate icon classes" configuration instead
 */
public final class FeaturesTrainerIcons {
  private static @NotNull Icon load(@NotNull String path, long cacheKey, int flags) {
    return IconManager.getInstance().loadRasterizedIcon(path, FeaturesTrainerIcons.class.getClassLoader(), cacheKey, flags);
  }

  public static final class Img {
    /** 16x16 */ public static final @NotNull Icon Checkmark = load("img/checkmark.svg", 6907338944229206668L, 2);
    /** 16x16 */ public static final @NotNull Icon FeatureTrainer = load("img/featureTrainer.svg", -6460187159917247672L, 2);
    /** 13x13 */ public static final @NotNull Icon FeatureTrainerToolWindow = load("img/featureTrainerToolWindow.svg", -5555050524412059250L, 2);
    /** 16x16 */ public static final @NotNull Icon GreenCheckmark = load("img/greenCheckmark.svg", -5456882804508189998L, 2);
    /** 40x40 */ public static final @NotNull Icon PluginIcon = load("img/pluginIcon.svg", 8850918341785325000L, 0);
    /** 16x16 */ public static final @NotNull Icon ResetLesson = load("img/resetLesson.svg", 3168924485572023045L, 2);
  }

  public static final class LearnProjects {
    public static final class Idea {
      /** 72x72 */ public static final @NotNull Icon Icon = load("learnProjects/.idea/icon.png", 0L, 0);
    }
  }

  public static final class METAINF {
    /** 40x40 */ public static final @NotNull Icon PluginIcon = load("META-INF/pluginIcon.svg", 8850918341785325000L, 0);
  }
}
