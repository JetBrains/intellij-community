// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Represents a tool which manages IDE updates instead of a built-in update engine.
 */
public enum ExternalUpdateManager {
  TOOLBOX("Toolbox App"),
  SNAP("Snap"),
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
    else if (SystemInfo.isLinux && (home.startsWith("/snap/") || home.startsWith("/var/lib/snapd/snap/"))) ACTUAL = SNAP;
    else if (System.getProperty("ide.no.platform.update") != null) ACTUAL = UNKNOWN;
    else ACTUAL = null;
  }

  /**
   * Returns {@code true} when the update manager takes care of creating XDG desktop entries.
   */
  public static boolean isCreatingDesktopEntries() {
    return ACTUAL == TOOLBOX || ACTUAL == SNAP;
  }
}
