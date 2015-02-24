/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * Utility wrappers for accessing system properties.
 *
 * @author yole
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public class SystemProperties {
  private static String ourTestUserName;

  private SystemProperties() { }

  /**
   * Returns the value of the user.home system property.
   *
   * @return the property value
   */
  public static String getUserHome() {
    return System.getProperty("user.home");
  }

  public static String getUserName() {
    return ourTestUserName != null ? ourTestUserName : System.getProperty("user.name");
  }

  @TestOnly
  public static void setTestUserName(@Nullable String name) {
    ourTestUserName = name;
  }

  /**
   * Returns the value of the line.separator system property.
   *
   * @return the property value
   */
  public static String getLineSeparator() {
    return System.getProperty("line.separator");
  }

  /**
   * Returns the value of the os.name system property.
   *
   * @return the property value
   */
  public static String getOsName() {
    return System.getProperty("os.name");
  }

  /**
   * Returns the value of the java.version system property. If you need to compare java version with some value use
   * {@link com.intellij.openapi.util.SystemInfo#isJavaVersionAtLeast(String)} instead
   *
   * @return the property value
   */
  public static String getJavaVersion() {
    return System.getProperty("java.version");
  }

  /**
   * Returns the value of the java.vm.vendor system property.
   *
   * @return the property value
   */
  public static String getJavaVmVendor() {
    return System.getProperty("java.vm.vendor");
  }

  /**
   * Returns the value of the java.home system property.
   *
   * @return the property value
   */
  public static String getJavaHome() {
    return System.getProperty("java.home");
  }

  /**
   * Returns the value of given property as integer.
   * Returns {@code defaultValue} if property is not specified or malformed.
   *
   * @param key          the property name
   * @param defaultValue default value
   * @return the property value as integer, or default value.
   */
  public static int getIntProperty(@NonNls @NotNull final String key, final int defaultValue) {
    final String value = System.getProperty(key);
    if (value != null) {
      try {
        return Integer.parseInt(value);
      }
      catch (NumberFormatException ignored) { }
    }

    return defaultValue;
  }

  /**
   * Returns the value of given property as a boolean.
   * Returns {@code defaultValue} if property is not specified or malformed.
   *
   * @param key          the property name
   * @param defaultValue default value
   * @return the property value as boolean, or default value.
   */
  public static boolean getBooleanProperty(@NotNull final String key, final boolean defaultValue) {
    final String value = System.getProperty(key);
    if (value != null) {
      return Boolean.parseBoolean(value);
    }

    return defaultValue;
  }

  public static String getJavaVendor() {
    return System.getProperty("java.vendor");
  }

  public static boolean is(String key) {
    return getBooleanProperty(key, false);
  }

  public static boolean has(String key) {
    return System.getProperty(key) != null;
  }
}
