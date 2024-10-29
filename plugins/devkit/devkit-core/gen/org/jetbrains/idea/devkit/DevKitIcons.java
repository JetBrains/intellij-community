// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  private static @NotNull Icon load(@NotNull String expUIPath, @NotNull String path, int cacheKey, int flags) {
    return IconManager.getInstance().loadRasterizedIcon(path, expUIPath, DevKitIcons.class.getClassLoader(), cacheKey, flags);
  }
  /** 16x16 */ public static final @NotNull Icon Add_sdk = load("icons/expui/addSDK.svg", "icons/add_sdk.svg", 641117830, 2);

  public static final class Gutter {
    /** 12x12 */ public static final @NotNull Icon DescriptionFile = load("icons/expui/gutter/descriptionFile@14x14.svg", "icons/gutter/descriptionFile.svg", 1318760137, 2);
    /** 12x12 */ public static final @NotNull Icon Diff = load("icons/expui/gutter/diff@14x14.svg", "icons/gutter/diff.svg", 124039984, 2);
    /** 12x12 */ public static final @NotNull Icon Plugin = load("icons/expui/gutter/plugin@14x14.svg", "icons/gutter/plugin.svg", 1850322899, 2);
    /** 12x12 */ public static final @NotNull Icon Properties = load("icons/expui/gutter/properties@14x14.svg", "icons/gutter/properties.svg", -818710709, 2);
  }

  /** 16x16 */ public static final @NotNull Icon PluginV2 = load("icons/expui/pluginV2.svg", 1719825147, 2);
  /** 16x16 */ public static final @NotNull Icon RemoteMapping = load("icons/expui/remoteMapping.svg", "icons/remoteMapping.svg", 1371307852, 2);
  /** 16x16 */ public static final @NotNull Icon Sdk_closed = load("icons/expui/sdkClosed.svg", "icons/sdk_closed.svg", -1355048140, 2);
}
