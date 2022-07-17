package com.intellij.grazie.icons;

import com.intellij.ui.IconManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * NOTE THIS FILE IS AUTO-GENERATED
 * DO NOT EDIT IT BY HAND, run "Generate icon classes" configuration instead
 */
public final class GrazieIcons {
  private static @NotNull Icon load(@NotNull String path, int cacheKey, int flags) {
    return IconManager.getInstance().loadRasterizedIcon(path, GrazieIcons.class.getClassLoader(), cacheKey, flags);
  }
  /** 16x16 */ public static final @NotNull Icon GrammarError = load("icons/grammarError.svg", 1531410605, 2);
  /** 16x16 */ public static final @NotNull Icon StyleError = load("icons/styleError.svg", 2060433281, 2);
  /** 16x16 */ public static final @NotNull Icon StyleSuggestion = load("icons/styleSuggestion.svg", 2100257017, 2);
  /** 16x16 */ public static final @NotNull Icon StyleWarning = load("icons/styleWarning.svg", 139367264, 2);
}
