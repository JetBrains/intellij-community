// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package icons;

import com.intellij.ui.IconManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * NOTE THIS FILE IS AUTO-GENERATED
 * DO NOT EDIT IT BY HAND, run "Generate icon classes" configuration instead
 */
public final class DevkitIcons {
  private static @NotNull Icon load(@NotNull String path, long cacheKey, int flags) {
    return IconManager.getInstance().loadRasterizedIcon(path, DevkitIcons.class.getClassLoader(), cacheKey, flags);
  }
  /** 16x16 */ public static final @NotNull Icon Add_sdk = load("icons/add_sdk.svg", -2223851075053996075L, 2);

  public static final class Gutter {
    /** 12x12 */ public static final @NotNull Icon DescriptionFile = load("icons/gutter/descriptionFile.svg", -2349580438727387051L, 2);
    /** 12x12 */ public static final @NotNull Icon Diff = load("icons/gutter/diff.svg", -3883728935072498509L, 2);
    /** 12x12 */ public static final @NotNull Icon Plugin = load("icons/gutter/plugin.svg", 6054120737400200886L, 2);
    /** 12x12 */ public static final @NotNull Icon Properties = load("icons/gutter/properties.svg", 6705305400152164505L, 2);
  }

  /** 16x16 */ public static final @NotNull Icon Sdk_closed = load("icons/sdk_closed.svg", 5906225570821378377L, 2);
}
