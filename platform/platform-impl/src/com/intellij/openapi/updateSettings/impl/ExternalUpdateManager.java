// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.system.OS;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Represents a tool which manages IDE updates instead of a built-in update engine.
 */
public enum ExternalUpdateManager {
  TOOLBOX("Toolbox App"),
  SNAP("Snap"),
  BREW("Homebrew"),
  UNKNOWN(null);

  public final @NlsSafe String toolName;

  ExternalUpdateManager(@Nullable("`null` for unknown (a.k.a. 'other') update manager") String name) {
    if (name != null) {
      toolName = name;
    }
    else {
      // historically, TB used 'ide.no.platform.update=true' to disable platform updates;
      // an absence of its signature path means an abuse of the property by some external entity
      name = System.getProperty("ide.no.platform.update");
      toolName = "true".equalsIgnoreCase(name) ? "(unknown)" : name;
    }
  }

  /**
   * Returns a tool managing this IDE instance, or {@code null} when no such tool is detected.
   */
  public static final @Nullable ExternalUpdateManager ACTUAL;
  static {
    var home = PathManager.getHomePath().replace('\\', '/');
    var toolboxV2Path = System.getProperty("ide.managed.by.toolbox");
    if (home.contains("/apps/") && home.contains("/ch-")) ACTUAL = TOOLBOX;
    else if (toolboxV2Path != null && Files.exists(Path.of(toolboxV2Path)))ACTUAL = TOOLBOX;
    else if (OS.CURRENT == OS.Linux && (home.startsWith("/snap/") || home.startsWith("/var/lib/snapd/snap/"))) ACTUAL = SNAP;
    else if (OS.CURRENT != OS.Windows && home.contains("/homebrew/")) ACTUAL = BREW;
    else if (System.getProperty("ide.no.platform.update") != null) ACTUAL = UNKNOWN;
    else ACTUAL = null;
    Logger.getInstance(ExternalUpdateManager.class).info("update manager: " + (ACTUAL == null ? "-" : ACTUAL.toolName));
  }

  /**
   * Returns {@code true} when the update manager takes care of creating XDG desktop entries.
   */
  @ApiStatus.Internal
  public static boolean isCreatingDesktopEntries() {
    return ACTUAL == TOOLBOX || ACTUAL == SNAP;
  }
}
