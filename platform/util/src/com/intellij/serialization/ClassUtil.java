// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.serialization;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.*;
import java.util.*;

public final class ClassUtil {
  private ClassUtil() {
  }

  public static @NotNull Class<?> getRawType(@NotNull Type type) {
    if (type instanceof Class) {
      return (Class<?>)type;
    }
    if (type instanceof ParameterizedType) {
      return getRawType(((ParameterizedType)type).getRawType());
    }
    if (type instanceof GenericArrayType) {
      //todo[peter] don't create new instance each time
      return Array.newInstance(getRawType(((GenericArrayType)type).getGenericComponentType()), 0).getClass();
    }
    assert false : type;
    return null;
  }

  public static @NotNull Class<?> typeToClass(@NotNull Type type) {
    if (type instanceof Class) {
      return (Class<?>)type;
    }
    else if (type instanceof TypeVariable) {
      Type bound = ((TypeVariable<?>)type).getBounds()[0];
      return bound instanceof Class ? (Class<?>)bound : (Class<?>)((ParameterizedType)bound).getRawType();
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

      String fullName = aClass.getName();
      if (fullName.equals("java.util.Arrays$ArrayList") ||
          fullName.equals("java.util.Collections$SingletonList") ||
          fullName.equals("com.intellij.util.containers.FreezableArrayList") ||
          fullName.equals("java.util.Collections$SingletonSet")) {
        return false;
      }

      Class<?> declaringClass = aClass.getDeclaringClass();
      return declaringClass == null || !"ImmutableCollections".equals(declaringClass.getSimpleName());
    }
    else {
      return false;
    }
  }

  public static boolean isMutableMap(@NotNull Map<?, ?> object) {
    if (object == Collections.emptyMap()) {
      return false;
    }
    else {
      String simpleName = object.getClass().getSimpleName();
      return !simpleName.equals("EmptyMap") &&
          !simpleName.equals("UnmodifiableMap") &&
          !simpleName.equals("ImmutableMap") &&
          !simpleName.equals("SingletonMap");
    }
  }

  public static @Nullable Object stringToEnum(@NotNull String value,
                                              @NotNull Class<? extends Enum<?>> valueClass,
                                              boolean isAlwaysIgnoreCase) {
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

  public static Type resolveVariableInHierarchy(@NotNull TypeVariable<?> variable, @NotNull Class<?> aClass) {
    Type type;
    Class<?> current = aClass;
    while ((type = resolveVariable(variable, current, false)) == null) {
      current = current.getSuperclass();
      if (current == null) {
        return null;
      }
    }
    if (type instanceof TypeVariable) {
      return resolveVariableInHierarchy((TypeVariable<?>)type, aClass);
    }
    return type;
  }

  public static @Nullable Type resolveVariable(@NotNull TypeVariable<?> variable,
                                               @NotNull Class<?> classType,
                                               boolean resolveInInterfacesOnly) {
    Class<?> aClass = getRawType(classType);
    int index = ArrayUtilRt.find(aClass.getTypeParameters(), variable);
    if (index >= 0) {
      return variable;
    }

    final Class<?>[] classes = aClass.getInterfaces();
    final Type[] genericInterfaces = aClass.getGenericInterfaces();
    for (int i = 0; i <= classes.length; i++) {
      Class<?> anInterface;
      if (i < classes.length) {
        anInterface = classes[i];
      }
      else {
        anInterface = aClass.getSuperclass();
        if (resolveInInterfacesOnly || anInterface == null) {
          continue;
        }
      }
      final Type resolved = resolveVariable(variable, anInterface, true);
      if (resolved instanceof Class || resolved instanceof ParameterizedType) {
        return resolved;
      }
      if (resolved instanceof TypeVariable) {
        final TypeVariable<?> typeVariable = (TypeVariable<?>)resolved;
        index = ArrayUtilRt.find(anInterface.getTypeParameters(), typeVariable);
        if (index < 0) {
          Logger.getInstance(ClassUtil.class)
            .error("Cannot resolve type variable:\n" + "typeVariable = " + typeVariable + "\n" + "genericDeclaration = " +
                   declarationToString(typeVariable.getGenericDeclaration()) + "\n" + "searching in " + declarationToString(anInterface));
        }
        final Type type = i < genericInterfaces.length ? genericInterfaces[i] : aClass.getGenericSuperclass();
        if (type instanceof Class) {
          return Object.class;
        }
        if (type instanceof ParameterizedType) {
          return ((ParameterizedType)type).getActualTypeArguments()[index];
        }
        throw new AssertionError("Invalid type: " + type);
      }
    }
    return null;
  }

  private static @NotNull String declarationToString(@NotNull GenericDeclaration anInterface) {
    return anInterface.toString() +
           Arrays.asList(anInterface.getTypeParameters()) +
           " loaded by " +
           ((Class<?>)anInterface).getClassLoader();
  }
}
