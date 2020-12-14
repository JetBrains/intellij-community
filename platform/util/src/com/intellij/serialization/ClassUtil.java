// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Map;

public final class ClassUtil {
  private ClassUtil() {
  }

  @NotNull
  public static Class<?> typeToClass(@NotNull Type type) {
    if (type instanceof Class) {
      return (Class<?>)type;
    }
    else if (type instanceof TypeVariable) {
      Type bound = ((TypeVariable)type).getBounds()[0];
      return bound instanceof Class ? (Class)bound : (Class<?>)((ParameterizedType)bound).getRawType();
    }
    else if (type instanceof WildcardType) {
      return (Class<?>)((WildcardType)type).getUpperBounds()[0];
    }
    else {
      return (Class<?>)((ParameterizedType)type).getRawType();
    }
  }

  public static boolean isPrimitive(@NotNull Class<?> aClass) {
    return aClass.isPrimitive() ||
           aClass == String.class ||
           aClass == Integer.class ||
           aClass == Long.class ||
           aClass == Boolean.class ||
           aClass == Double.class ||
           aClass == Float.class ||
           aClass.isEnum() ||
           Date.class.isAssignableFrom(aClass);
  }

  public static boolean isMutableCollection(@NotNull Object object) {
    if (object == Collections.emptyList() || object == Collections.emptySet()) {
      return false;
    }
    else if (object instanceof Collection) {
      Class<?> aClass = object.getClass();
      String simpleName = aClass.getSimpleName();
      if (simpleName.equals("EmptyList") || simpleName.startsWith("Unmodifiable") || simpleName.equals("EmptySet")) {
        return false;
      }

      Class<?> declaringClass = aClass.getDeclaringClass();
      return declaringClass == null || !"ImmutableCollections".equals(declaringClass.getSimpleName());
    }
    else {
      return false;
    }
  }

  public static boolean isMutableMap(@NotNull Map object) {
    if (object == Collections.emptyMap()) {
      return false;
    }
    else {
      String simpleName = object.getClass().getSimpleName();
      return !simpleName.equals("EmptyMap") && !simpleName.equals("UnmodifiableMap");
    }
  }

  @Nullable
  public static Object stringToEnum(@NotNull String value, @NotNull Class<? extends Enum<?>> valueClass, boolean isAlwaysIgnoreCase) {
    Enum<?>[] enumConstants = valueClass.getEnumConstants();
    if (!isAlwaysIgnoreCase) {
      for (Object enumConstant : enumConstants) {
        if (enumConstant.toString().equals(value)) {
          return enumConstant;
        }
      }
    }
    for (Object enumConstant : enumConstants) {
      if (enumConstant.toString().equalsIgnoreCase(value)) {
        return enumConstant;
      }
    }
    return null;
  }
}
