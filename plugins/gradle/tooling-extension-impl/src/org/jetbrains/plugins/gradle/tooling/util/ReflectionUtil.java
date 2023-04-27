// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class ReflectionUtil {
  public static <T> T reflectiveGetProperty(Object target, String propertyName, Class<T> aClass) {
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

  public static  <T> T reflectiveCall(Object target, String methodName, Class<T> aClass) {
    try {
      Method getProperty = target.getClass().getMethod(methodName);
      Object value = getProperty.invoke(target);
      return aClass.cast(value);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
