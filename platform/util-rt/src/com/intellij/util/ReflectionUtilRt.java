// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.diagnostic.LoggerRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ReflectionUtilRt {
  @NotNull
  public static List<Field> collectFields(@NotNull Class<?> clazz) {
    List<Field> result = new ArrayList<>();
    collectFields(clazz, result);
    return result;
  }

  private static void collectFields(@NotNull Class<?> clazz, @NotNull List<? super Field> result) {
    result.addAll(Arrays.asList(clazz.getDeclaredFields()));

    Class<?> superClass = clazz.getSuperclass();
    if (superClass != null) {
      collectFields(superClass, result);
    }

    for (Class<?> each : clazz.getInterfaces()) {
      collectFields(each, result);
    }
  }

  @NotNull
  public static List<Method> collectGetters(@NotNull Class<?> clazz) {
    List<Method> methods = collectMethods(clazz);
    List<Method> result = new ArrayList<>();
    for (Method method: methods) {
      String methodName = method.getName();
      if (methodName.startsWith("get") && method.getParameterTypes().length == 0 && !methodName.equals("getClass")) {
        result.add(method);
      }
    }
    return result;
  }

  @NotNull
  public static List<Method> collectMethods(@NotNull Class<?> clazz) {
    List<Method> result = new ArrayList<>();
    collectMethods(clazz, result);
    return result;
  }

  private static void collectMethods(@NotNull Class<?> clazz, @NotNull List<? super Method> result) {
    result.addAll(Arrays.asList(clazz.getDeclaredMethods()));

    Class<?> superClass = clazz.getSuperclass();
    if (superClass != null) {
      collectMethods(superClass, result);
    }

    for (Class<?> each : clazz.getInterfaces()) {
      collectMethods(each, result);
    }
  }

  public static <T> T getField(@NotNull Class<?> objectClass,
                               @Nullable Object object,
                               @Nullable("null means any type") Class<T> fieldType,
                               @NotNull String fieldName) {
    Field field = findField(objectClass, fieldName, fieldType);
    if (field != null) {
      try {
        //noinspection unchecked
        return (T)field.get(object);
      }
      catch (IllegalAccessException ignored) { }
    }

    return null;
  }

  @Nullable
  private static Field findField(@NotNull Class<?> clazz, @NotNull String fieldName, @Nullable Class<?> fieldType) {
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

  private static final class MySecurityManager extends SecurityManager {
    private static final MySecurityManager INSTANCE = new MySecurityManager();
    Class<?>[] getStack() {
      return getClassContext();
    }
  }

  /**
   * Returns the class this method was called 'framesToSkip' frames up the caller hierarchy.
   *
   * NOTE:
   * <b>Extremely expensive!
   * Please consider not using it.
   * These aren't the droids you're looking for!</b>
   */
  @Nullable
  public static Class<?> findCallerClass(int framesToSkip) {
    try {
      Class<?>[] stack = MySecurityManager.INSTANCE.getStack();
      int indexFromTop = 1 + framesToSkip;
      return stack.length > indexFromTop ? stack[indexFromTop] : null;
    }
    catch (Exception e) {
      LoggerRt.getInstance(ReflectionUtilRt.class).warn(e);
      return null;
    }
  }
}