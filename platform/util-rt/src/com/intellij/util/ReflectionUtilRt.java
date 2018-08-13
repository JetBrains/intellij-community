// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ReflectionUtilRt {
  @NotNull
  public static List<Field> collectFields(@NotNull Class clazz) {
    List<Field> result = new ArrayList<Field>();
    collectFields(clazz, result);
    return result;
  }

  private static void collectFields(@NotNull Class clazz, @NotNull List<Field> result) {
    result.addAll(Arrays.asList(clazz.getDeclaredFields()));
    final Class superClass = clazz.getSuperclass();
    if (superClass != null) {
      collectFields(superClass, result);
    }
    final Class[] interfaces = clazz.getInterfaces();
    for (Class each : interfaces) {
      collectFields(each, result);
    }
  }

  public static <T> T getField(@NotNull Class objectClass,
                               @Nullable Object object,
                               @Nullable("null means any type") Class<T> fieldType,
                               @NotNull @NonNls String fieldName) {
    try {
      final Field field = findAssignableField(objectClass, fieldType, fieldName);
      @SuppressWarnings("unchecked") T t = (T)field.get(object);
      return t;
    }
    catch (NoSuchFieldException e) {
      return null;
    }
    catch (IllegalAccessException e) {
      return null;
    }
  }

  @NotNull
  private static Field findAssignableField(@NotNull Class<?> clazz,
                                          @Nullable("null means any type") final Class<?> fieldType,
                                          @NotNull final String fieldName) throws NoSuchFieldException {
    Field result = processFields(clazz, new Condition<Field>() {
      public boolean value(Field field) {
        return fieldName.equals(field.getName()) && (fieldType == null || fieldType.isAssignableFrom(field.getType()));
      }
    });
    if (result != null) return result;
    throw new NoSuchFieldException("Class: " + clazz + " fieldName: " + fieldName + " fieldType: " + fieldType);
  }

  private static Field processFields(@NotNull Class clazz, @NotNull Condition<Field> checker) {
    for (Field field : clazz.getDeclaredFields()) {
      if (checker.value(field)) {
        field.setAccessible(true);
        return field;
      }
    }
    final Class superClass = clazz.getSuperclass();
    if (superClass != null) {
      Field result = processFields(superClass, checker);
      if (result != null) return result;
    }
    final Class[] interfaces = clazz.getInterfaces();
    for (Class each : interfaces) {
      Field result = processFields(each, checker);
      if (result != null) return result;
    }
    return null;
  }
}
