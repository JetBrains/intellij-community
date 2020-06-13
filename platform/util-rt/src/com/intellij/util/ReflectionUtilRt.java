// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ReflectionUtilRt {
  @NotNull
  public static List<Field> collectFields(@NotNull Class<?> clazz) {
    List<Field> result = new ArrayList<Field>();
    collectFields(clazz, result);
    return result;
  }

  private static void collectFields(Class<?> clazz, List<? super Field> result) {
    result.addAll(Arrays.asList(clazz.getDeclaredFields()));

    Class<?> superClass = clazz.getSuperclass();
    if (superClass != null) {
      collectFields(superClass, result);
    }

    for (Class<?> each : clazz.getInterfaces()) {
      collectFields(each, result);
    }
  }

  public static <T> T getField(@NotNull Class<?> objectClass,
                               @Nullable Object object,
                               @Nullable("null means any type") Class<T> fieldType,
                               @NotNull String fieldName) {
    Field field = findField(objectClass, fieldName, fieldType);
    if (field != null) {
      try {
        @SuppressWarnings("unchecked") T t = (T)field.get(object);
        return t;
      }
      catch (IllegalAccessException ignored) { }
    }

    return null;
  }

  @Nullable
  private static Field findField(Class<?> clazz, String fieldName, @Nullable Class<?> fieldType) {
    for (Field field : clazz.getDeclaredFields()) {
      if (fieldName.equals(field.getName()) && (fieldType == null || fieldType.isAssignableFrom(field.getType()))) {
        field.setAccessible(true);
        return field;
      }
    }

    Class<?> superClass = clazz.getSuperclass();
    if (superClass != null) {
      Field result = findField(superClass, fieldName, fieldType);
      if (result != null) return result;
    }

    for (Class<?> each : clazz.getInterfaces()) {
      Field result = findField(each, fieldName, fieldType);
      if (result != null) return result;
    }

    return null;
  }
}