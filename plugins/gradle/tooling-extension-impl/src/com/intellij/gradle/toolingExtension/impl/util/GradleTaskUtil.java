// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.util;

import org.gradle.api.Task;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class GradleTaskUtil {

  private static @Nullable Object getProperty(@NotNull Task task, @NotNull String propertyName) {
    ExtensionContainer extensions = task.getExtensions();
    ExtraPropertiesExtension propertiesExtension = extensions.getExtraProperties();
    if (!propertiesExtension.has(propertyName)) {
      return null;
    }
    return propertiesExtension.get(propertyName);
  }

  public static boolean getBooleanProperty(@NotNull Task task, @NotNull String propertyName, boolean defaultValue) {
    Object value = getProperty(task, propertyName);
    if (value == null) {
      return defaultValue;
    }
    return Boolean.parseBoolean(value.toString());
  }
}
