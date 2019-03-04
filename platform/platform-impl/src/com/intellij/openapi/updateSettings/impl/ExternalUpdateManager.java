// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a tool which manages IDE updates instead of a built-in update engine.
 */
public enum ExternalUpdateManager {
  TOOLBOX("Toolbox"),
  SNAP("Snap"),
  UNKNOWN(null);

  public final String toolName;

  ExternalUpdateManager(String name) {
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
    String home = PathManager.getHomePath().replace('\\', '/');
    if (home.contains("/apps/") && home.contains("/ch-")) ACTUAL = TOOLBOX;
    else if (SystemInfo.isLinux && (home.startsWith("/snap/") || home.startsWith("/var/lib/snapd/snap/"))) ACTUAL = SNAP;
    else if (System.getProperty("ide.no.platform.update") != null) ACTUAL = UNKNOWN;
    else ACTUAL = null;
  }

  /**
   * Returns {@code true} when updates are managed by a tool which install different builds in different directories.
   */
  public static boolean isRoaming() {
    return ACTUAL == TOOLBOX || ACTUAL == SNAP;
  }
}