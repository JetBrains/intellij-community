// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.openapi.util.NotNullFactory;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;

public final class ObjectUtils {
  private ObjectUtils() { }

  /** @see NotNullizer */
  public static final Object NULL = sentinel("ObjectUtils.NULL");

  /**
   * Creates a new object which could be used as a sentinel value (special value to distinguish from any other object).
   * It does not equal to any other object.
   * Usually should be assigned to the static final field.
   *
   * @param name an object name, returned from {@link #toString()} to simplify the debugging or heap dump analysis
   *             (guaranteed to be stored as sentinel object field). If sentinel is assigned to the static final field,
   *             it's recommended to supply that field name (possibly qualified with the class name).
   * @return a new sentinel object
   */
  public static @NotNull Object sentinel(@NotNull @NonNls String name) {
    return new Sentinel(name);
  }

  private static final class Sentinel {
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
  public static @NotNull <T> T sentinel(@NotNull String name, @NotNull Class<T> ofInterface) {
    if (!ofInterface.isInterface()) {
      throw new IllegalArgumentException("Expected interface but got: " + ofInterface);
    }
    // java.lang.reflect.Proxy.ProxyClassFactory fails if the class is not available via the classloader.
    // We must use interface own classloader because classes from plugins are not available via ObjectUtils' classloader.
    return ReflectionUtil.proxy(ofInterface, (__, method, args) -> {
      if ("toString".equals(method.getName()) && args.length == 0) {
        return name;
      }
      throw new AbstractMethodError();
    });
  }

  /**
   * @deprecated Use {@link Objects#requireNonNull(Object)}
   */
  @Deprecated
  public static @NotNull <T> T assertNotNull(@Nullable T t) {
    return Objects.requireNonNull(t);
  }

  public static <T> void assertAllElementsNotNull(T @NotNull [] array) {
    int i = ArrayUtil.indexOfIdentity(array, null);
    if (i != -1) {
      throw new NullPointerException("Element [" + i + "] is null");
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

  public static @Nullable <T> T coalesce(@NotNull Iterable<? extends T> o) {
    for (T t : o) {
      if (t != null) return t;
    }
    return null;
  }

  /**
   * @deprecated Use {@link Objects#requireNonNull(Object)}
   */
  @Deprecated
  public static <T> T notNull(@Nullable T value) {
    return Objects.requireNonNull(value);
  }

  @Contract(value = "null, _ -> param2; !null, _ -> param1", pure = true)
  public static @NotNull <T> T notNull(@Nullable T value, @NotNull T defaultValue) {
    return value == null ? defaultValue : value;
  }

  public static @NotNull <T> T notNull(@Nullable T value, @NotNull NotNullFactory<? extends T> defaultValue) {
    return value == null ? defaultValue.create() : value;
  }

  @Contract(value = "null, _ -> null", pure = true)
  public static @Nullable <T> T tryCast(@Nullable Object obj, @NotNull Class<T> clazz) {
    return clazz.isInstance(obj) ? clazz.cast(obj) : null;
  }

  /**
   * Do not use in Kotlin.
   */
  public static @Nullable <T, S> S doIfCast(@Nullable Object obj,
                                            @NotNull Class<T> clazz,
                                            @NotNull Convertor<? super T, ? extends S> convertor) {
    //noinspection unchecked
    return clazz.isInstance(obj) ? convertor.convert((T)obj) : null;
  }

  @Contract("null, _ -> null")
  public static @Nullable <T, S> S doIfNotNull(@Nullable T obj, @NotNull Function<? super T, ? extends S> function) {
    return obj == null ? null : function.fun(obj);
  }

  /**
   * @deprecated Use {@code if (obj != null) ...} instead
   */
  @Deprecated
  public static <T> void consumeIfNotNull(@Nullable T obj, @NotNull Consumer<? super T> consumer) {
    if (obj != null) {
      consumer.consume(obj);
    }
  }

  /**
   * @deprecated this method is unnecessary. Just write if statement (use pattern variable when possible).
   */
  @Deprecated
  public static <T> void consumeIfCast(@Nullable Object obj, @NotNull Class<T> clazz, @NotNull Consumer<? super T> consumer) {
    if (clazz.isInstance(obj)) {
      //noinspection unchecked
      T t = (T)obj;
      consumer.consume(t);
    }
  }

  @Contract("null, _ -> null")
  public static @Nullable <T> T nullizeByCondition(@Nullable T obj, @NotNull Predicate<? super T> condition) {
    return condition.test(obj) ? null : obj;
  }

  @Contract("null, _ -> null")
  public static @Nullable <T> T nullizeIfDefaultValue(@Nullable T obj, @NotNull T defaultValue) {
    return obj == defaultValue ? null : obj;
  }

  /**
   * Performs binary search on the range [fromIndex, toIndex)
   * @param indexComparator a comparator which receives a middle index and returns the result of comparison of the value at this index and the goal value
   *                        (e.g 0 if found, -1 if the value[middleIndex] < goal, or 1 if value[middleIndex] > goal)
   * @return index for which {@code indexComparator} returned 0 or {@code -insertionIndex-1} if wasn't found
   * @see java.util.Arrays#binarySearch(Object[], Object, Comparator)
   * @see java.util.Collections#binarySearch(List, Object, Comparator)
   */
  public static int binarySearch(int fromIndex, int toIndex, @NotNull IntUnaryOperator indexComparator) {
    int low = fromIndex;
    int high = toIndex - 1;
    while (low <= high) {
      int mid = (low + high) >>> 1;
      int cmp = indexComparator.applyAsInt(mid);
      if (cmp < 0) low = mid + 1;
      else if (cmp > 0) high = mid - 1;
      else return mid;
    }
    return -(low + 1);
  }

  public static @NotNull String objectInfo(@Nullable Object o) {
    return o != null ? o + " (" + o.getClass().getName() + ")" : "null";
  }
}
