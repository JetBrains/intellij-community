// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Utility wrappers for accessing system properties.
 *
 * @see com.intellij.openapi.util.SystemInfo
 */
public final class SystemProperties {
  private SystemProperties() { }

  public static @NotNull String getUserHome() {
    return System.getProperty("user.home");
  }

  public static @NotNull String getUserName() {
    return System.getProperty("user.name");
  }

  public static @NotNull String getJavaHome() {
    return System.getProperty("java.home");
  }

  /**
   * Returns a value of the given property as an integer, or {@code defaultValue} if the property is not specified or malformed.
   */
  public static int getIntProperty(@NotNull String key, int defaultValue) {
    String value = System.getProperty(key);
    if (value != null) {
      try { return Integer.parseInt(value); }
      catch (NumberFormatException ignored) { }
    }
    return defaultValue;
  }

  /**
   * Returns a value of the given property as a float, or {@code defaultValue} if the property is not specified or malformed.
   */
  public static float getFloatProperty(@NotNull String key, float defaultValue) {
    String value = System.getProperty(key);
    if (value != null) {
      try { return Float.parseFloat(value); }
      catch (NumberFormatException ignored) { }
    }
    return defaultValue;
  }

  /**
   * Returns a value of the given property as a boolean, or {@code defaultValue} if the property is not specified or malformed.
   */
  public static boolean getBooleanProperty(@NotNull String key, boolean defaultValue) {
    String value = System.getProperty(key);
    return value != null ? Boolean.parseBoolean(value) : defaultValue;
  }

  public static boolean is(String key) {
    return getBooleanProperty(key, false);
  }

  public static boolean has(String key) {
    return System.getProperty(key) != null;
  }

  //<editor-fold desc="Deprecated stuff.">
  /** @deprecated please use {@link System#lineSeparator()} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
  public static String getLineSeparator() {
    return System.lineSeparator();
  }

  /** @deprecated moved to {@link com.intellij.openapi.editor.EditorCoreUtil} */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
  public static boolean isTrueSmoothScrollingEnabled() {
    return getBooleanProperty("idea.true.smooth.scrolling", false);
  }
  //</editor-fold>
}
