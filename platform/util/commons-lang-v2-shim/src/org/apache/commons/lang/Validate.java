// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.apache.commons.lang;

/**
 * @deprecated To continue using the functionality offered by commons-lang2,
 * please consider migrating to either the commons-lang3 or commons-text libraries and bundling them with your plugin.
 * Or consider using the corresponding API from IJ Platform.
 */
@SuppressWarnings("unused")
@Deprecated(forRemoval = true)
public class Validate extends org.apache.commons.lang3.Validate {
  public static void notEmpty(String string, String message) {
    if (string == null || string.isEmpty()) {
      throw new IllegalArgumentException(message);
    }
  }

  public static void notEmpty(String string) {
    if (string == null || string.isEmpty()) {
      throw new IllegalArgumentException("The validated string is empty");
    }
  }

  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static <T> T notNull(T object) {
    return org.apache.commons.lang3.Validate.notNull(object);
  }
}
