// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ReflectionUtil {
  public static <T> T reflectiveGetProperty(Object target, String propertyName, Class<T> aClass)
    throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    Method getProperty = target.getClass().getMethod(propertyName);
    Object property = getProperty.invoke(target);
    Method get = property.getClass().getMethod("get");
    Object value = get.invoke(property);
    return aClass.cast(value);
  }

}
