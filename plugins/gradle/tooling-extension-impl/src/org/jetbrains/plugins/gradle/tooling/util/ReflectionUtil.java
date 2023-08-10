// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

public final class ReflectionUtil {
  public static <T> T reflectiveGetProperty(@NotNull Object target, @NotNull String propertyName, @NotNull Class<T> aClass) {
    try {
      Method getProperty = target.getClass().getMethod(propertyName);
      Object property = getProperty.invoke(target);
      Method get = property.getClass().getMethod("get");
      Object value = get.invoke(property);
      return aClass.cast(value);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static <T> T reflectiveCall(@NotNull Object target, @NotNull String methodName, @NotNull Class<T> aClass) {
    try {
      Method getProperty = target.getClass().getMethod(methodName);
      Object value = getProperty.invoke(target);
      return aClass.cast(value);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static boolean dynamicCheckInstanceOf(@NotNull Object object, String... classNames) {
    for (String className : classNames) {
      Class<?> clazz = findClassForName(className);
      if (clazz != null && clazz.isInstance(object)) {
        return true;
      }
    }
    return false;
  }

  public static @Nullable Class<?> findClassForName(@NotNull String className) {
    try {
      return Class.forName(className);
    }
    catch (ClassNotFoundException __) {
      return null;
    }
  }
}
