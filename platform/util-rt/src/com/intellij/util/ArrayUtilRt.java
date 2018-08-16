// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;

/**
 * Stripped-down version of {@code com.intellij.util.ArrayUtil}.
 * Intended to use by external (out-of-IDE-process) runners and helpers so it should not contain any library dependencies.
 *
 * @since 12.0
 */
@SuppressWarnings({"UtilityClassWithoutPrivateConstructor", "SSBasedInspection"})
public class ArrayUtilRt {
  public static final short[] EMPTY_SHORT_ARRAY = new short[0];
  public static final char[] EMPTY_CHAR_ARRAY = new char[0];
  public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
  public static final int[] EMPTY_INT_ARRAY = new int[0];
  public static final boolean[] EMPTY_BOOLEAN_ARRAY = new boolean[0];
  public static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];
  public static final String[] EMPTY_STRING_ARRAY = new String[0];
  public static final Class[] EMPTY_CLASS_ARRAY = new Class[0];
  public static final long[] EMPTY_LONG_ARRAY = new long[0];
  public static final Collection[] EMPTY_COLLECTION_ARRAY = new Collection[0];
  public static final File[] EMPTY_FILE_ARRAY = new File[0];
  public static final Runnable[] EMPTY_RUNNABLE_ARRAY = new Runnable[0];

  @NotNull
  @Contract(pure=true)
  public static String[] toStringArray(@Nullable Collection<String> collection) {
    return collection == null || collection.isEmpty()
           ? EMPTY_STRING_ARRAY : ContainerUtilRt.toArray(collection, new String[collection.size()]);
  }

  /**
   * @param src source array.
   * @param obj object to be found.
   * @return index of {@code obj} in the {@code src} array.
   *         Returns {@code -1} if passed object isn't found. This method uses
   *         {@code equals} of arrays elements to compare {@code obj} with
   *         these elements.
   */
  @Contract(pure=true)
  public static <T> int find(@NotNull final T[] src, @Nullable final T obj) {
    for (int i = 0; i < src.length; i++) {
      final T o = src[i];
      if (o == null) {
        if (obj == null) {
          return i;
        }
      }
      else {
        if (o.equals(obj)) {
          return i;
        }
      }
    }
    return -1;
  }
}