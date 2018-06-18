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
import com.intellij.openapi.util.NotNullFactory;
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

  public static final Object NULL = sentinel("ObjectUtils.NULL");

  /**
   * Creates a new object which could be used as sentinel value (special value to distinguish from any other object). It does not equal
   * to any other object. Usually should be assigned to the static final field.
   *
   * @param name an object name, returned from {@link #toString()} to simplify the debugging or heap dump analysis
   *             (guaranteed to be stored as sentinel object field). If sentinel is assigned to the static final field,
   *             it's recommended to supply that field name (possibly qualified with the class name).
   * @return a new sentinel object
   */
  public static Object sentinel(final String name) {
    return new Sentinel(name);
  }

  @NotNull
  public static <T> T assertNotNull(@Nullable T t) {
    return notNull(t);
  }

  public static <T> void assertAllElementsNotNull(@NotNull T[] array) {
    for (int i = 0; i < array.length; i++) {
      T t = array[i];
      if (t == null) {
        throw new NullPointerException("Element [" + i + "] is null");
      }
    }
  }

  @Contract(value = "!null, _ -> !null; _, !null -> !null; _, _ -> null", pure = true)
  public static <T> T chooseNotNull(@Nullable T t1, @Nullable T t2) {
    return t1 == null? t2 : t1;
  }

  @Contract(value = "!null, _ -> !null; _, !null -> !null; _, _ -> null", pure = true)
  public static <T> T coalesce(@Nullable T t1, @Nullable T t2) {
    return chooseNotNull(t1, t2);
  }

  @Contract(value = "!null, _, _ -> !null; _, !null, _ -> !null; _, _, !null -> !null; _,_,_ -> null", pure = true)
  public static <T> T coalesce(@Nullable T t1, @Nullable T t2, @Nullable T t3) {
    return t1 != null ? t1 : t2 != null ? t2 : t3;
  }

  @Nullable
  public static <T> T coalesce(@Nullable Iterable<T> o) {
    if (o == null) return null;
    for (T t : o) {
      if (t != null) return t;
    }
    return null;
  }

  @NotNull
  public static <T> T notNull(@Nullable T value) {
    //noinspection ConstantConditions
    return notNull(value, value);
  }

  @NotNull
  @Contract(pure = true)
  public static <T> T notNull(@Nullable T value, @NotNull T defaultValue) {
    return value == null ? defaultValue : value;
  }

  @NotNull
  public static <T> T notNull(@Nullable T value, @NotNull NotNullFactory<T> defaultValue) {
    return value == null ? defaultValue.create() : value;
  }

  @Contract(value = "null, _ -> null", pure = true)
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

  @Contract("null, _ -> null")
  @Nullable
  public static <T, S> S doIfNotNull(@Nullable T obj, @NotNull Function<T, S> function) {
    return obj == null ? null : function.fun(obj);
  }

  @SuppressWarnings("unchecked")
  public static <T> void consumeIfCast(@Nullable Object obj, @NotNull Class<T> clazz, final Consumer<T> consumer) {
    if (clazz.isInstance(obj)) consumer.consume((T)obj);
  }

  @Nullable
  @Contract("null, _ -> null")
  public static <T> T nullizeByCondition(@Nullable final T obj, @NotNull final Condition<T> condition) {
    if (condition.value(obj)) {
      return null;
    }
    return obj;
  }

  private static class Sentinel {
    private final String myName;

    public Sentinel(String name) {myName = name;}

    @Override
    public String toString() {
      return myName;
    }
  }
}
