/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;

/**
 * Contrary to the name, this class doesn't do any caching. So the usages may be safely dropped in favor of plain reflection calls.
 * 
 * Consider caching higher-level things, if you see reflection in your snapshots.
 *
 * @deprecated
 * @author peter
 */
@SuppressWarnings({"MismatchedQueryAndUpdateOfCollection"})
public class ReflectionCache {

  @Deprecated
  public static Class getSuperClass(@NotNull Class aClass) {
    return aClass.getSuperclass();
  }

  @NotNull
  @Deprecated
  public static Class[] getInterfaces(@NotNull Class aClass) {
    return aClass.getInterfaces();
  }

  @NotNull
  @Deprecated
  public static Method[] getMethods(@NotNull Class aClass) {
    return aClass.getMethods();
  }

  /**
   * @deprecated
   * @see com.intellij.util.ReflectionUtil#isAssignable(Class, Class)
   */
  public static boolean isAssignable(@NotNull Class ancestor, Class descendant) {
    return ancestor == descendant || ancestor.isAssignableFrom(descendant);
  }

  @Deprecated
  public static boolean isInstance(Object instance, @NotNull Class clazz) {
    return clazz.isInstance(instance);
  }

  @Deprecated
  public static boolean isInterface(@NotNull Class aClass) {
    return aClass.isInterface();
  }

  @NotNull
  @Deprecated
  public static <T> TypeVariable<Class<T>>[] getTypeParameters(@NotNull Class<T> aClass) {
    return aClass.getTypeParameters();
  }

  @NotNull
  @Deprecated
  public static Type[] getGenericInterfaces(@NotNull Class aClass) {
    return aClass.getGenericInterfaces();
  }

  @NotNull
  @Deprecated
  public static Type[] getActualTypeArguments(@NotNull ParameterizedType type) {
    return type.getActualTypeArguments();
  }

}
