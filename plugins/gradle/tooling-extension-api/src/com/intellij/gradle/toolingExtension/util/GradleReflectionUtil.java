// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

public final class GradleReflectionUtil {

  public static boolean hasMethod(@NotNull Object target, @NotNull String methodName, Class<?>... parameterTypes) {
    try {
      target.getClass().getMethod(methodName, parameterTypes);
      return true;
    }
    catch (NoSuchMethodException | SecurityException e) {
      return false;
    }
  }

  public static <T> T reflectiveCall(@NotNull Object target, @NotNull String methodName, @NotNull Class<T> aClass) {
    try {
      Method method = target.getClass().getMethod(methodName);
      Object value = method.invoke(target);
      return aClass.cast(value);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static boolean isInstance(@NotNull Object object, String className) {
    Class<?> clazz = findClassForName(className);
    return clazz != null && clazz.isInstance(object);
  }

  public static @Nullable Class<?> findClassForName(@NotNull String className) {
    try {
      return Class.forName(className);
    }
    catch (ClassNotFoundException __) {
      return null;
    }
  }

  public static @Nullable Class<?> loadClassOrNull(@NotNull ClassLoader classLoader, @NotNull String className) {
    try {
      return classLoader.loadClass(className);
    }
    catch (ClassNotFoundException __) {
      return null;
    }
  }
}
