// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.openapi.util.text.StringUtilRt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Null-safe {@code equal} methods.
 */
public final class Comparing {
  private Comparing() { }

  @Contract(value = "null,!null -> false; !null,null -> false; null,null -> true", pure = true)
  public static <T> boolean equal(@Nullable T arg1, @Nullable T arg2) {
    if (arg1 == arg2) return true;
    if (arg1 == null || arg2 == null) {
      return false;
    }
    if (arg1 instanceof Object[] && arg2 instanceof Object[]) {
      Object[] arr1 = (Object[])arg1;
      Object[] arr2 = (Object[])arg2;
      return Arrays.equals(arr1, arr2);
    }
    if (arg1 instanceof CharSequence && arg2 instanceof CharSequence) {
      return equal((CharSequence)arg1, (CharSequence)arg2, true);
    }
    return arg1.equals(arg2);
  }

  /** @deprecated same as {@link Arrays#equals(Object[], Object[])} */
  @Deprecated @ApiStatus.ScheduledForRemoval
  public static <T> boolean equal(@Nullable T[] arr1, @Nullable T[] arr2) {
    return Arrays.equals(arr1, arr2);
  }

  @Contract(value = "null,!null -> false; !null,null -> false; null,null -> true", pure = true)
  public static boolean equal(CharSequence s1, CharSequence s2) {
    return StringUtilRt.equal(s1, s2, true);
  }

  @Contract(value = "null,!null,_ -> false; !null,null,_ -> false; null,null,_ -> true", pure = true)
  public static boolean equal(@Nullable CharSequence s1, @Nullable CharSequence s2, boolean caseSensitive) {
    return StringUtilRt.equal(s1, s2, caseSensitive);
  }

  /**
   * @deprecated Use {@link Objects#equals(Object, Object)}
   */
  @Deprecated
  @Contract(value = "null,!null -> false; !null,null -> false; null,null -> true", pure = true)
  public static boolean equal(@Nullable String arg1, @Nullable String arg2) {
    return arg1 == null ? arg2 == null : arg1.equals(arg2);
  }

  @Contract(value = "null,!null,_ -> false; !null,null,_ -> false; null,null,_ -> true", pure = true)
  public static boolean equal(@Nullable String arg1, @Nullable String arg2, boolean caseSensitive) {
    return arg1 == null ? arg2 == null : caseSensitive ? arg1.equals(arg2) : arg1.equalsIgnoreCase(arg2);
  }

  /** Unlike {@link Objects#equals(Object, Object)}, considers {@code null} and {@code ""} equal. */
  public static boolean strEqual(@Nullable String arg1, @Nullable String arg2) {
    return strEqual(arg1, arg2, true);
  }

  /** Unlike {@link #equal(String, String, boolean)}, considers {@code null} and {@code ""} equal. */
  public static boolean strEqual(@Nullable String arg1, @Nullable String arg2, boolean caseSensitive) {
    return equal(arg1 == null ? "" : arg1, arg2 == null ? "" : arg2, caseSensitive);
  }

  public static <T> boolean haveEqualElements(@NotNull Collection<? extends T> a, @NotNull Collection<? extends T> b) {
    if (a.size() != b.size()) {
      return false;
    }

    Set<T> aSet = new HashSet<T>(a);
    for (T t : b) {
      if (!aSet.contains(t)) {
        return false;
      }
    }

    return true;
  }

  public static <T> boolean haveEqualElements(@Nullable T[] a, @Nullable T[] b) {
    if (a == null || b == null) {
      //noinspection ArrayEquality
      return a == b;
    }

    if (a.length != b.length) {
      return false;
    }

    Set<T> aSet = new HashSet<T>(Arrays.asList(a));
    for (T t : b) {
      if (!aSet.contains(t)) {
        return false;
      }
    }

    return true;
  }

  @SuppressWarnings("MethodNamesDifferingOnlyByCase")
  public static int hashcode(@Nullable Object obj) {
    return obj == null ? 0 : obj.hashCode();
  }

  public static int hashcode(Object obj1, Object obj2) {
    return hashcode(obj1) ^ hashcode(obj2);
  }

  /**
   * @see AbstractSet#hashCode()
   */
  public static int unorderedHashcode(@NotNull Collection<?> collection) {
    int h = 0;
    for (Object obj : collection) {
      if (obj != null) {
        h += obj.hashCode();
      }
    }
    return h;
  }

  public static int compare(byte o1, byte o2) {
    return o1 < o2 ? -1 : o1 == o2 ? 0 : 1;
  }

  public static int compare(boolean o1, boolean o2) {
    return o1 == o2 ? 0 : o1 ? 1 : -1;
  }

  public static int compare(int o1, int o2) {
    return o1 < o2 ? -1 : o1 == o2 ? 0 : 1;
  }

  public static int compare(long o1, long o2) {
    return o1 < o2 ? -1 : o1 == o2 ? 0 : 1;
  }

  public static int compare(double o1, double o2) {
    return Double.compare(o1, o2);
  }

  public static int compare(@Nullable byte[] o1, @Nullable byte[] o2) {
    //noinspection ArrayEquality
    if (o1 == o2) return 0;
    if (o1 == null) return 1;
    if (o2 == null) return -1;

    if (o1.length > o2.length) return 1;
    if (o1.length < o2.length) return -1;

    for (int i = 0; i < o1.length; i++) {
      if (o1[i] > o2[i]) return 1;
      else if (o1[i] < o2[i]) return -1;
    }

    return 0;
  }

  public static <T extends Comparable<? super T>> int compare(@Nullable T o1, @Nullable T o2) {
    if (o1 == o2) return 0;
    if (o1 == null) return -1;
    if (o2 == null) return 1;
    return o1.compareTo(o2);
  }

  public static <T> int compare(@Nullable T o1, @Nullable T o2, @NotNull Comparator<? super T> notNullComparator) {
    if (o1 == o2) return 0;
    if (o1 == null) return -1;
    if (o2 == null) return 1;
    return notNullComparator.compare(o1, o2);
  }
}
