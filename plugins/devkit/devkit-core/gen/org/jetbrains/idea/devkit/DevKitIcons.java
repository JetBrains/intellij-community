// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit;

import com.intellij.ui.IconManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * NOTE THIS FILE IS AUTO-GENERATED
 * DO NOT EDIT IT BY HAND, run "Generate icon classes" configuration instead
 */
public final class DevKitIcons {
  private static @NotNull Icon load(@NotNull String path, int cacheKey, int flags) {
    return IconManager.getInstance().loadRasterizedIcon(path, DevKitIcons.class.getClassLoader(), cacheKey, flags);
  }
  /** 16x16 */ public static final @NotNull Icon Add_sdk = load("icons/add_sdk.svg", -983487671, 2);

  public static final class Gutter {
    /** 12x12 */ public static final @NotNull Icon DescriptionFile = load("icons/gutter/descriptionFile.svg", 1471505269, 2);
    /** 12x12 */ public static final @NotNull Icon Diff = load("icons/gutter/diff.svg", 1564682497, 2);
    /** 12x12 */ public static final @NotNull Icon Plugin = load("icons/gutter/plugin.svg", -346227860, 2);
    /** 12x12 */ public static final @NotNull Icon Properties = load("icons/gutter/properties.svg", 775611747, 2);
  }

  /** 16x16 */ public static final @NotNull Icon Sdk_closed = load("icons/sdk_closed.svg", -862754932, 2);
}
