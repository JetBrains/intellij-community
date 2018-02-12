/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;

/**
 * Thread unsafe field accessor.
 *
 * @param <E> the type of the field's class
 * @param <T> the type of the field
 */
public class FieldAccessor<E, T> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.FieldAccessor");

  private Ref<Field> myFieldRef;
  private final Class<E> myClass;
  private final String myName;

  public FieldAccessor(@NotNull Class<E> cls, @NotNull String name) {
    myClass = cls;
    myName = name;
  }

  public boolean isAvailable() {
    if (myFieldRef == null) {
      try {
        myFieldRef = new Ref<Field>();
        myFieldRef.set(myClass.getDeclaredField(myName));
        myFieldRef.get().setAccessible(true);
      }
      catch (NoSuchFieldException e) {
        LOG.warn("Field not found: " + myClass.getName() + "." + myName);
      }
    }
    return myFieldRef.get() != null;
  }

  public T get(@Nullable E object) {
    if (!isAvailable()) return null;
    try {
      @SuppressWarnings("unchecked")
      T value = (T)myFieldRef.get().get(object);
      return value;
    }
    catch (IllegalAccessException e) {
      LOG.warn("Field not accessible: " + myClass.getName() + "." + myName);
    }
    return null;
  }

  public void set(@Nullable E object, @Nullable T value) {
    if (!isAvailable()) return;
    try {
      myFieldRef.get().set(object, value);
    }
    catch (IllegalAccessException e) {
      LOG.warn("Field not accessible: " + myClass.getName() + "." + myName);
    }
  }
}
