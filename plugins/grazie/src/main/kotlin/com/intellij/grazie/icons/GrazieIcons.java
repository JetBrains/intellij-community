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
  /** 16x16 */ public static final @NotNull Icon GrammarError = load("icons/grammarError.svg", 1619543618, 2);
  /** 16x16 */ public static final @NotNull Icon StyleError = load("icons/styleError.svg", 1747184725, 2);
  /** 16x16 */ public static final @NotNull Icon StyleSuggestion = load("icons/styleSuggestion.svg", 122697311, 2);
  /** 16x16 */ public static final @NotNull Icon StyleWarning = load("icons/styleWarning.svg", 395413890, 2);
}
