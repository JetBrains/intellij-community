// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  /** 16x16 */ public static final @NotNull Icon Grazie = load("icons/Grazie.svg", -1953760450, 0);
  /** 16x16 */ public static final @NotNull Icon GrazieCloudProcessing = load("icons/GrazieCloudProcessing.svg", -1136853606, 2);

  public static final class Stroke {
    /** 16x16 */ public static final @NotNull Icon Grazie = load("icons/stroke/Grazie.svg", -87121590, 2);
    /** 16x16 */ public static final @NotNull Icon GrazieCloudError = load("icons/stroke/GrazieCloudError.svg", -280256603, 2);
    /** 16x16 */ public static final @NotNull Icon GrazieCloudProcessing = load("icons/stroke/GrazieCloudProcessing.svg", -927088014, 2);
  }

  /** 16x16 */ public static final @NotNull Icon StyleSuggestion = load("icons/StyleSuggestion.svg", 150737608, 2);
}
