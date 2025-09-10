// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;

/**
 * A stripped-down version of {@link com.intellij.util.ArrayUtil}.
 * Intended to use by external (out-of-IDE-process) runners and helpers so it should not contain any library dependencies.
 */
public final class ArrayUtilRt {
  public static final short[] EMPTY_SHORT_ARRAY = new short[0];
  public static final char[] EMPTY_CHAR_ARRAY = new char[0];
  public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
  public static final int[] EMPTY_INT_ARRAY = new int[0];
  public static final boolean[] EMPTY_BOOLEAN_ARRAY = new boolean[0];
  @SuppressWarnings("SSBasedInspection")
  public static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];
  @SuppressWarnings("SSBasedInspection")
  public static final String[] EMPTY_STRING_ARRAY = new String[0];
  @SuppressWarnings("SSBasedInspection")
  public static final Class[] EMPTY_CLASS_ARRAY = new Class[0];
  public static final long[] EMPTY_LONG_ARRAY = new long[0];
  public static final Collection[] EMPTY_COLLECTION_ARRAY = new Collection[0];
  public static final File[] EMPTY_FILE_ARRAY = new File[0];

  private ArrayUtilRt() { }

  @Contract(pure=true)
  public static String @NotNull [] toStringArray(@Nullable Collection<String> collection) {
    return collection == null || collection.isEmpty()
           ? EMPTY_STRING_ARRAY : collection.toArray(EMPTY_STRING_ARRAY);
  }

  /**
   * @param src source array.
   * @param obj object to be found.
   * @return index of {@code obj} in the {@code src} array.
   *         Returns {@code -1} if passed object isn't found. This method uses
   *         {@code equals} of arrays elements to compare {@code obj} with
   *         these elements.
   */
  @Contract(pure = true)
  public static <T> int find(T @NotNull [] src, @Nullable T obj) {
    return indexOf(src, obj, 0, src.length);
  }

  @Contract(pure = true)
  public static <T> int indexOf(T @NotNull [] src, @Nullable T obj, int start, int end) {
    if (obj == null) {
      for (int i = start; i < end; i++) {
        if (src[i] == null) return i;
      }
    }
    else {
      for (int i = start; i < end; i++) {
        if (obj.equals(src[i])) return i;
      }
    }
    return -1;
  }
}