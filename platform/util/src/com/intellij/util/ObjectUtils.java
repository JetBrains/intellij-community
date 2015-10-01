/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.util.Condition;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class ObjectUtils {
  private ObjectUtils() {
  }

  public static final Object NULL = new Object();

  @NotNull
  public static <T> T assertNotNull(@Nullable final T t) {
    return _assertNotNull(t);
  }

  public static <T> void assertAllElementsNotNull(@NotNull T[] array) {
    for (int i = 0; i < array.length; i++) {
      T t = array[i];
      if (t == null) {
        throw new NullPointerException("Element [" + i + "] is null");
      }
    }
  }

  @NotNull
  private static <T> T _assertNotNull(@NotNull T t) {
    return t;
  }

  @Contract("null, null -> null")
  public static <T> T chooseNotNull(@Nullable T t1, @Nullable T t2) {
    return t1 == null? t2 : t1;
  }

  @NotNull
  public static <T> T notNull(@Nullable T value, @NotNull T defaultValue) {
    return value == null ? defaultValue : value;
  }

  @Nullable
  public static <T> T tryCast(@Nullable Object obj, @NotNull Class<T> clazz) {
    if (clazz.isInstance(obj)) {
      return clazz.cast(obj);
    }
    return null;
  }

  @Nullable
  public static <T, S> S doIfCast(@Nullable Object obj, @NotNull Class<T> clazz, final Convertor<T, S> convertor) {
    if (clazz.isInstance(obj)) {
      //noinspection unchecked
      return convertor.convert((T)obj);
    }
    return null;
  }

  @Nullable
  public static <T> T nullizeByCondition(@Nullable final T obj, @NotNull final Condition<T> condition) {
    if (condition.value(obj)) {
      return null;
    }
    return obj;
  }
}
