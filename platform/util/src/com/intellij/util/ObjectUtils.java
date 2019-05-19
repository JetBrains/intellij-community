// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.NotNullFactory;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Proxy;
import java.util.Comparator;
import java.util.List;

/**
 * @author peter
 */
public class ObjectUtils {
  private ObjectUtils() {
  }

  /**
   * @see NotNullizer
   */
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
  @NotNull
  public static Object sentinel(@NotNull String name) {
    return new Sentinel(name);
  }

  /**
   * They promise in http://mail.openjdk.java.net/pipermail/core-libs-dev/2018-February/051312.html that
   * the object reference won't be removed by JIT and GC-ed until this call.
  */
  public static void reachabilityFence(@SuppressWarnings("unused") Object o) {}

  private static class Sentinel {
    private final String myName;

    Sentinel(@NotNull String name) {
      myName = name;
    }

    @Override
    public String toString() {
      return myName;
    }
  }

  /**
   * Creates an instance of class {@code ofInterface} with its {@link Object#toString()} method returning {@code name}.
   * No other guarantees about return value behaviour.
   * {@code ofInterface} must represent an interface class.
   * Useful for stubs in generic code, e.g. for storing in {@code List<T>} to represent empty special value.
   */
  @NotNull
  public static <T> T sentinel(@NotNull final String name, @NotNull Class<T> ofInterface) {
    if (!ofInterface.isInterface()) {
      throw new IllegalArgumentException("Expected interface but got: " + ofInterface);
    }
    // java.lang.reflect.Proxy.ProxyClassFactory fails if the class is not available via the classloader.
    // We must use interface own classloader because classes from plugins are not available via ObjectUtils' classloader.
    //noinspection unchecked
    return (T)Proxy.newProxyInstance(ofInterface.getClassLoader(), new Class[]{ofInterface}, (proxy, method, args) -> {
      if ("toString".equals(method.getName()) && args.length == 0) {
        return name;
      }
      throw new AbstractMethodError();
    });
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

  @Contract(value = "!null, _ -> !null; _, !null -> !null; null, null -> null", pure = true)
  public static <T> T chooseNotNull(@Nullable T t1, @Nullable T t2) {
    return t1 == null? t2 : t1;
  }

  @Contract(value = "!null, _ -> !null; _, !null -> !null; null, null -> null", pure = true)
  public static <T> T coalesce(@Nullable T t1, @Nullable T t2) {
    return chooseNotNull(t1, t2);
  }

  @Contract(value = "!null, _, _ -> !null; _, !null, _ -> !null; _, _, !null -> !null; null,null,null -> null", pure = true)
  public static <T> T coalesce(@Nullable T t1, @Nullable T t2, @Nullable T t3) {
    return t1 != null ? t1 : t2 != null ? t2 : t3;
  }

  @Nullable
  public static <T> T coalesce(@Nullable Iterable<? extends T> o) {
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
  public static <T> T notNull(@Nullable T value, @NotNull NotNullFactory<? extends T> defaultValue) {
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
  public static <T, S> S doIfCast(@Nullable Object obj, @NotNull Class<T> clazz, final Convertor<? super T, ? extends S> convertor) {
    if (clazz.isInstance(obj)) {
      //noinspection unchecked
      return convertor.convert((T)obj);
    }
    return null;
  }

  @Contract("null, _ -> null")
  @Nullable
  public static <T, S> S doIfNotNull(@Nullable T obj, @NotNull Function<? super T, ? extends S> function) {
    return obj == null ? null : function.fun(obj);
  }

  public static <T> void consumeIfCast(@Nullable Object obj, @NotNull Class<T> clazz, final Consumer<? super T> consumer) {
    if (clazz.isInstance(obj)) {
      //noinspection unchecked
      consumer.consume((T)obj);
    }
  }

  @Nullable
  @Contract("null, _ -> null")
  public static <T> T nullizeByCondition(@Nullable final T obj, @NotNull final Condition<? super T> condition) {
    if (condition.value(obj)) {
      return null;
    }
    return obj;
  }

  /**
   * Performs binary search on the range [fromIndex, toIndex)
   * @param indexComparator a comparator which receives a middle index and returns the result of comparision of the value at this index and the goal value
   *                        (e.g 0 if found, -1 if the value[middleIndex] < goal, or 1 if value[middleIndex] > goal)
   * @return index for which {@code indexComparator} returned 0 or {@code -insertionIndex-1} if wasn't found
   * @see java.util.Arrays#binarySearch(Object[], Object, Comparator)
   * @see java.util.Collections#binarySearch(List, Object, Comparator)
   */
  public static int binarySearch(int fromIndex, int toIndex, @NotNull IntIntFunction indexComparator) {
    int low = fromIndex;
    int high = toIndex - 1;
    while (low <= high) {
      int mid = (low + high) >>> 1;
      int cmp = indexComparator.fun(mid);
      if (cmp < 0) low = mid + 1;
      else if (cmp > 0) high = mid - 1;
      else return mid;
    }
    return -(low + 1);
  }
}
