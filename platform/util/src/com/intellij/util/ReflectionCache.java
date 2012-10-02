/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.util.containers.ConcurrentFactoryMap;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.*;

/**
 * @author peter
 */
@SuppressWarnings({"MismatchedQueryAndUpdateOfCollection"})
public class ReflectionCache {
  private static final ConcurrentFactoryMap<Class,Class> ourSuperClasses = new ConcurrentFactoryMap<Class, Class>() {
    @Override
    protected Class create(final Class key) {
      return key.getSuperclass();
    }
  };
  private static final ConcurrentFactoryMap<Class,Class[]> ourInterfaces = new ConcurrentFactoryMap<Class, Class[]>() {
    @Override
    @NotNull
    protected Class[] create(final Class key) {
      Class[] classes = key.getInterfaces();
      return classes.length == 0 ? ArrayUtil.EMPTY_CLASS_ARRAY : classes;
    }
  };

  private static final ConcurrentFactoryMap<Class,Boolean> ourIsInterfaces = new ConcurrentFactoryMap<Class, Boolean>() {
    @Override
    @NotNull
    protected Boolean create(final Class key) {
      return key.isInterface();
    }
  };
  private static final ConcurrentFactoryMap<Class, TypeVariable[]> ourTypeParameters = new ConcurrentFactoryMap<Class, TypeVariable[]>() {
    @Override
    @NotNull
    protected TypeVariable[] create(final Class key) {
      return key.getTypeParameters();
    }
  };
  private static final ConcurrentFactoryMap<Class, Type[]> ourGenericInterfaces = new ConcurrentFactoryMap<Class, Type[]>() {
    @Override
    @NotNull
    protected Type[] create(final Class key) {
      return key.getGenericInterfaces();
    }
  };
  private static final ConcurrentFactoryMap<ParameterizedType, Type[]> ourActualTypeArguments = new ConcurrentFactoryMap<ParameterizedType, Type[]>() {
    @Override
    @NotNull
    protected Type[] create(final ParameterizedType key) {
      return key.getActualTypeArguments();
    }
  };

  private ReflectionCache() {
  }

  public static Class getSuperClass(@NotNull Class aClass) {
    return ourSuperClasses.get(aClass);
  }

  @NotNull
  public static Class[] getInterfaces(@NotNull Class aClass) {
    return ourInterfaces.get(aClass);
  }

  @NotNull
  public static Method[] getMethods(@NotNull Class aClass) {
    return aClass.getMethods();
  }

  public static boolean isAssignable(@NotNull Class ancestor, Class descendant) {
    return ancestor == descendant || ancestor.isAssignableFrom(descendant);
  }

  public static boolean isInstance(Object instance, @NotNull Class clazz) {
    return clazz.isInstance(instance);
  }

  public static boolean isInterface(@NotNull Class aClass) {
    return ourIsInterfaces.get(aClass);
  }

  @NotNull
  public static <T> TypeVariable<Class<T>>[] getTypeParameters(@NotNull Class<T> aClass) {
    return ourTypeParameters.get(aClass);
  }

  @NotNull
  public static Type[] getGenericInterfaces(@NotNull Class aClass) {
    return ourGenericInterfaces.get(aClass);
  }

  @NotNull
  public static Type[] getActualTypeArguments(@NotNull ParameterizedType type) {
    return ourActualTypeArguments.get(type);
  }

}
