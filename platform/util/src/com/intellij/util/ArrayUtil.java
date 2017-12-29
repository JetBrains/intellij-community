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

import com.intellij.openapi.util.Comparing;
import com.intellij.util.text.CharArrayCharSequence;
import gnu.trove.Equality;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Author: msk
 */
@SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
public class ArrayUtil extends ArrayUtilRt {
  public static final short[] EMPTY_SHORT_ARRAY = ArrayUtilRt.EMPTY_SHORT_ARRAY;
  public static final char[] EMPTY_CHAR_ARRAY = ArrayUtilRt.EMPTY_CHAR_ARRAY;
  public static final byte[] EMPTY_BYTE_ARRAY = ArrayUtilRt.EMPTY_BYTE_ARRAY;
  public static final int[] EMPTY_INT_ARRAY = ArrayUtilRt.EMPTY_INT_ARRAY;
  public static final boolean[] EMPTY_BOOLEAN_ARRAY = ArrayUtilRt.EMPTY_BOOLEAN_ARRAY;
  public static final Object[] EMPTY_OBJECT_ARRAY = ArrayUtilRt.EMPTY_OBJECT_ARRAY;
  public static final String[] EMPTY_STRING_ARRAY = ArrayUtilRt.EMPTY_STRING_ARRAY;
  public static final Class[] EMPTY_CLASS_ARRAY = ArrayUtilRt.EMPTY_CLASS_ARRAY;
  public static final long[] EMPTY_LONG_ARRAY = ArrayUtilRt.EMPTY_LONG_ARRAY;
  public static final Collection[] EMPTY_COLLECTION_ARRAY = ArrayUtilRt.EMPTY_COLLECTION_ARRAY;
  public static final File[] EMPTY_FILE_ARRAY = ArrayUtilRt.EMPTY_FILE_ARRAY;
  public static final Runnable[] EMPTY_RUNNABLE_ARRAY = ArrayUtilRt.EMPTY_RUNNABLE_ARRAY;
  public static final CharSequence EMPTY_CHAR_SEQUENCE = new CharArrayCharSequence(EMPTY_CHAR_ARRAY);

  public static final ArrayFactory<String> STRING_ARRAY_FACTORY = new ArrayFactory<String>() {
    @NotNull
    @Override
    public String[] create(int count) {
      return newStringArray(count);
    }
  };
  public static final ArrayFactory<Object> OBJECT_ARRAY_FACTORY = new ArrayFactory<Object>() {
    @NotNull
    @Override
    public Object[] create(int count) {
      return newObjectArray(count);
    }
  };

  private ArrayUtil() { }

  @NotNull
  @Contract(pure=true)
  public static byte[] realloc(@NotNull byte[] array, final int newSize) {
    if (newSize == 0) {
      return EMPTY_BYTE_ARRAY;
    }

    final int oldSize = array.length;
    if (oldSize == newSize) {
      return array;
    }

    final byte[] result = new byte[newSize];
    System.arraycopy(array, 0, result, 0, Math.min(oldSize, newSize));
    return result;
  }
  @NotNull
  @Contract(pure=true)
  public static boolean[] realloc(@NotNull boolean[] array, final int newSize) {
    if (newSize == 0) {
      return EMPTY_BOOLEAN_ARRAY;
    }

    final int oldSize = array.length;
    if (oldSize == newSize) {
      return array;
    }

    boolean[] result = new boolean[newSize];
    System.arraycopy(array, 0, result, 0, Math.min(oldSize, newSize));
    return result;
  }

  @NotNull
  @Contract(pure=true)
  public static long[] realloc(@NotNull long[] array, int newSize) {
    if (newSize == 0) {
      return EMPTY_LONG_ARRAY;
    }

    final int oldSize = array.length;
    if (oldSize == newSize) {
      return array;
    }

    long[] result = new long[newSize];
    System.arraycopy(array, 0, result, 0, Math.min(oldSize, newSize));
    return result;
  }
  @NotNull
  @Contract(pure=true)
  public static int[] realloc(@NotNull int[] array, final int newSize) {
    if (newSize == 0) {
      return EMPTY_INT_ARRAY;
    }

    final int oldSize = array.length;
    if (oldSize == newSize) {
      return array;
    }

    final int[] result = new int[newSize];
    System.arraycopy(array, 0, result, 0, Math.min(oldSize, newSize));
    return result;
  }
  @NotNull
  @Contract(pure=true)
  public static <T> T[] realloc(@NotNull T[] array, final int newSize, @NotNull ArrayFactory<T> factory) {
    final int oldSize = array.length;
    if (oldSize == newSize) {
      return array;
    }

    T[] result = factory.create(newSize);
    if (newSize == 0) {
      return result;
    }

    System.arraycopy(array, 0, result, 0, Math.min(oldSize, newSize));
    return result;
  }

  @NotNull
  @Contract(pure=true)
  public static long[] append(@NotNull long[] array, long value) {
    array = realloc(array, array.length + 1);
    array[array.length - 1] = value;
    return array;
  }
  @NotNull
  @Contract(pure=true)
  public static int[] append(@NotNull int[] array, int value) {
    array = realloc(array, array.length + 1);
    array[array.length - 1] = value;
    return array;
  }

  @NotNull
  @Contract(pure=true)
  public static <T> T[] insert(@NotNull T[] array, int index, T value) {
    T[] result = createArray(array.getClass().getComponentType(), array.length + 1);
    System.arraycopy(array, 0, result, 0, index);
    result[index] = value;
    System.arraycopy(array, index, result, index + 1, array.length - index);
    return result;
  }

  @NotNull
  @Contract(pure=true)
  public static int[] insert(@NotNull int[] array, int index, int value) {
    int[] result = new int[array.length + 1];
    System.arraycopy(array, 0, result, 0, index);
    result[index] = value;
    System.arraycopy(array, index, result, index+1, array.length - index);
    return result;
  }

  @NotNull
  @Contract(pure=true)
  public static byte[] append(@NotNull byte[] array, byte value) {
    array = realloc(array, array.length + 1);
    array[array.length - 1] = value;
    return array;
  }
  @NotNull
  @Contract(pure=true)
  public static boolean[] append(@NotNull boolean[] array, boolean value) {
    array = realloc(array, array.length + 1);
    array[array.length - 1] = value;
    return array;
  }

  @NotNull
  @Contract(pure=true)
  public static char[] realloc(@NotNull char[] array, final int newSize) {
    if (newSize == 0) {
      return EMPTY_CHAR_ARRAY;
    }

    final int oldSize = array.length;
    if (oldSize == newSize) {
      return array;
    }

    final char[] result = new char[newSize];
    System.arraycopy(array, 0, result, 0, Math.min(oldSize, newSize));
    return result;
  }

  @NotNull
  @Contract(pure=true)
  public static <T> T[] toObjectArray(@NotNull Collection<? extends T> collection, @NotNull Class<T> aClass) {
    T[] array = createArray(aClass, collection.size());
    return collection.toArray(array);
  }

  @NotNull
  @Contract(pure=true)
  public static <T> T[] toObjectArray(@NotNull Class<T> aClass, @NotNull Object... source) {
    T[] array = createArray(aClass, source.length);
    //noinspection SuspiciousSystemArraycopy
    System.arraycopy(source, 0, array, 0, array.length);
    return array;
  }

  @NotNull
  @Contract(pure=true)
  public static Object[] toObjectArray(@NotNull Collection<?> collection) {
    if (collection.isEmpty()) return EMPTY_OBJECT_ARRAY;
    //noinspection SSBasedInspection
    return collection.toArray(new Object[collection.size()]);
  }

  @NotNull
  @Contract(pure=true)
  public static int[] toIntArray(@NotNull Collection<Integer> list) {
    int[] ret = newIntArray(list.size());
    int i = 0;
    for (Integer e : list) {
      ret[i++] = e.intValue();
    }
    return ret;
  }

  @NotNull
  @Contract(pure=true)
  public static <T> T[] mergeArrays(@NotNull T[] a1, @NotNull T[] a2) {
    if (a1.length == 0) {
      return a2;
    }
    if (a2.length == 0) {
      return a1;
    }

    final Class<?> class1 = a1.getClass().getComponentType();
    final Class<?> class2 = a2.getClass().getComponentType();
    final Class<?> aClass = class1.isAssignableFrom(class2) ? class1 : class2;

    T[] result = createArray(aClass, a1.length + a2.length);
    System.arraycopy(a1, 0, result, 0, a1.length);
    System.arraycopy(a2, 0, result, a1.length, a2.length);
    return result;
  }

  @NotNull
  @Contract(pure=true)
  public static <T> T[] mergeCollections(@NotNull Collection<? extends T> c1, @NotNull Collection<? extends T> c2, @NotNull ArrayFactory<T> factory) {
    T[] res = factory.create(c1.size() + c2.size());

    int i = 0;

    for (T t : c1) {
      res[i++] = t;
    }

    for (T t : c2) {
      res[i++] = t;
    }

    return res;
  }

  @NotNull
  @Contract(pure=true)
  public static <T> T[] mergeArrays(@NotNull T[] a1, @NotNull T[] a2, @NotNull ArrayFactory<T> factory) {
    if (a1.length == 0) {
      return a2;
    }
    if (a2.length == 0) {
      return a1;
    }
    T[] result = factory.create(a1.length + a2.length);
    System.arraycopy(a1, 0, result, 0, a1.length);
    System.arraycopy(a2, 0, result, a1.length, a2.length);
    return result;
  }

  @NotNull
  @Contract(pure=true)
  public static String[] mergeArrays(@NotNull String[] a1, @NotNull String... a2) {
    return mergeArrays(a1, a2, STRING_ARRAY_FACTORY);
  }

  @NotNull
  @Contract(pure=true)
  public static int[] mergeArrays(@NotNull int[] a1, @NotNull int[] a2) {
    if (a1.length == 0) {
      return a2;
    }
    if (a2.length == 0) {
      return a1;
    }
    int[] result = new int[a1.length + a2.length];
    System.arraycopy(a1, 0, result, 0, a1.length);
    System.arraycopy(a2, 0, result, a1.length, a2.length);
    return result;
  }

  @NotNull
  @Contract(pure=true)
  public static byte[] mergeArrays(@NotNull byte[] a1, @NotNull byte[] a2) {
    if (a1.length == 0) {
      return a2;
    }
    if (a2.length == 0) {
      return a1;
    }
    byte[] result = new byte[a1.length + a2.length];
    System.arraycopy(a1, 0, result, 0, a1.length);
    System.arraycopy(a2, 0, result, a1.length, a2.length);
    return result;
  }

  /**
   * Allocates new array of size {@code array.length + collection.size()} and copies elements of {@code array} and
   * {@code collection} to it.
   *
   * @param array      source array
   * @param collection source collection
   * @param factory    array factory used to create destination array of type {@code T}
   * @return destination array
   */
  @NotNull
  @Contract(pure=true)
  public static <T> T[] mergeArrayAndCollection(@NotNull T[] array,
                                                @NotNull Collection<T> collection,
                                                @NotNull final ArrayFactory<T> factory) {
    if (collection.isEmpty()) {
      return array;
    }

    final T[] array2;
    try {
      array2 = collection.toArray(factory.create(collection.size()));
    }
    catch (ArrayStoreException e) {
      throw new RuntimeException("Bad elements in collection: " + collection, e);
    }

    if (array.length == 0) {
      return array2;
    }

    final T[] result = factory.create(array.length + collection.size());
    System.arraycopy(array, 0, result, 0, array.length);
    System.arraycopy(array2, 0, result, array.length, array2.length);
    return result;
  }

  /**
   * Appends {@code element} to the {@code src} array. As you can
   * imagine the appended element will be the last one in the returned result.
   *
   * @param src     array to which the {@code element} should be appended.
   * @param element object to be appended to the end of {@code src} array.
   * @return new array
   */
  @NotNull
  @Contract(pure=true)
  public static <T> T[] append(@NotNull final T[] src, @Nullable final T element) {
    //noinspection unchecked
    return append(src, element, (Class<T>)src.getClass().getComponentType());
  }

  @NotNull
  @Contract(pure=true)
  public static <T> T[] prepend(final T element, @NotNull final T[] array) {
    //noinspection unchecked
    return prepend(element, array, (Class<T>)array.getClass().getComponentType());
  }

  @NotNull
  @Contract(pure=true)
  public static <T> T[] prepend(T element, @NotNull T[] array, @NotNull Class<T> type) {
    int length = array.length;
    T[] result = createArray(type, length + 1);
    System.arraycopy(array, 0, result, 1, length);
    result[0] = element;
    return result;
  }

  @NotNull
  @Contract(pure=true)
  public static <T> T[] prepend(final T element, @NotNull final T[] src, @NotNull ArrayFactory<T> factory) {
    int length = src.length;
    T[] result = factory.create(length + 1);
    System.arraycopy(src, 0, result, 1, length);
    result[0] = element;
    return result;
  }

  @NotNull
  @Contract(pure=true)
  public static byte[] prepend(byte element, @NotNull byte[] array) {
    int length = array.length;
    final byte[] result = new byte[length + 1];
    result[0] = element;
    System.arraycopy(array, 0, result, 1, length);
    return result;
  }

  @NotNull
  @Contract(pure=true)
  public static <T> T[] append(@NotNull final T[] src, final T element, @NotNull ArrayFactory<T> factory) {
    int length = src.length;
    T[] result = factory.create(length + 1);
    System.arraycopy(src, 0, result, 0, length);
    result[length] = element;
    return result;
  }

  @NotNull
  @Contract(pure=true)
  public static <T> T[] append(@NotNull T[] src, @Nullable final T element, @NotNull Class<T> componentType) {
    int length = src.length;
    T[] result = createArray(componentType, length + 1);
    System.arraycopy(src, 0, result, 0, length);
    result[length] = element;
    return result;
  }

  /**
   * Removes element with index {@code idx} from array {@code src}.
   *
   * @param src array.
   * @param idx index of element to be removed.
   * @return modified array.
   */
  @NotNull
  @Contract(pure=true)
  public static <T> T[] remove(@NotNull final T[] src, int idx) {
    int length = src.length;
    if (idx < 0 || idx >= length) {
      throw new IllegalArgumentException("invalid index: " + idx);
    }
    T[] result = createArray(src.getClass().getComponentType(), length - 1);
    System.arraycopy(src, 0, result, 0, idx);
    System.arraycopy(src, idx + 1, result, idx, length - idx - 1);
    return result;
  }

  @NotNull
  private static <T> T[] createArray(@NotNull Class<?> type, int length) {
    //noinspection unchecked
    return (T[])Array.newInstance(type, length);
  }

  @NotNull
  @Contract(pure=true)
  public static <T> T[] remove(@NotNull final T[] src, int idx, @NotNull ArrayFactory<T> factory) {
    int length = src.length;
    if (idx < 0 || idx >= length) {
      throw new IllegalArgumentException("invalid index: " + idx);
    }
    T[] result = factory.create(length - 1);
    System.arraycopy(src, 0, result, 0, idx);
    System.arraycopy(src, idx + 1, result, idx, length - idx - 1);
    return result;
  }

  @NotNull
  @Contract(pure=true)
  public static <T> T[] remove(@NotNull final T[] src, T element) {
    final int idx = find(src, element);
    if (idx == -1) return src;

    return remove(src, idx);
  }

  @NotNull
  @Contract(pure=true)
  public static <T> T[] remove(@NotNull final T[] src, T element, @NotNull ArrayFactory<T> factory) {
    final int idx = find(src, element);
    if (idx == -1) return src;

    return remove(src, idx, factory);
  }

  @NotNull
  @Contract(pure=true)
  public static int[] remove(@NotNull final int[] src, int idx) {
    int length = src.length;
    if (idx < 0 || idx >= length) {
      throw new IllegalArgumentException("invalid index: " + idx);
    }
    int[] result = newIntArray(src.length - 1);
    System.arraycopy(src, 0, result, 0, idx);
    System.arraycopy(src, idx + 1, result, idx, length - idx - 1);
    return result;
  }
  @NotNull
  @Contract(pure=true)
  public static short[] remove(@NotNull final short[] src, int idx) {
    int length = src.length;
    if (idx < 0 || idx >= length) {
      throw new IllegalArgumentException("invalid index: " + idx);
    }
    short[] result = src.length == 1 ? EMPTY_SHORT_ARRAY : new short[src.length - 1];
    System.arraycopy(src, 0, result, 0, idx);
    System.arraycopy(src, idx + 1, result, idx, length - idx - 1);
    return result;
  }

  @Contract(pure=true)
  public static int find(@NotNull int[] src, int obj) {
    return indexOf(src, obj);
  }

  @Contract(pure=true)
  public static <T> int find(@NotNull final T[] src, final T obj) {
    return ArrayUtilRt.find(src, obj);
  }

  @Contract(pure=true)
  public static boolean startsWith(@NotNull byte[] array, @NotNull byte[] prefix) {
    //noinspection ArrayEquality
    if (array == prefix) {
      return true;
    }
    int length = prefix.length;
    if (array.length < length) {
      return false;
    }

    for (int i = 0; i < length; i++) {
      if (array[i] != prefix[i]) {
        return false;
      }
    }

    return true;
  }

  @Contract(pure=true)
  public static <E> boolean startsWith(@NotNull E[] array, @NotNull E[] subArray) {
    //noinspection ArrayEquality
    if (array == subArray) {
      return true;
    }
    int length = subArray.length;
    if (array.length < length) {
      return false;
    }

    for (int i = 0; i < length; i++) {
      if (!Comparing.equal(array[i], subArray[i])) {
        return false;
      }
    }

    return true;
  }

  @Contract(pure=true)
  public static boolean startsWith(@NotNull byte[] array, int start, @NotNull byte[] subArray) {
    int length = subArray.length;
    if (array.length - start < length) {
      return false;
    }

    for (int i = 0; i < length; i++) {
      if (array[start + i] != subArray[i]) {
        return false;
      }
    }

    return true;
  }

  @Contract(pure=true)
  public static <T> boolean equals(@NotNull T[] a1, @NotNull T[] a2, @NotNull Equality<? super T> comparator) {
    //noinspection ArrayEquality
    if (a1 == a2) {
      return true;
    }

    int length = a2.length;
    if (a1.length != length) {
      return false;
    }

    for (int i = 0; i < length; i++) {
      if (!comparator.equals(a1[i], a2[i])) {
        return false;
      }
    }
    return true;
  }

  @Contract(pure=true)
  public static <T> boolean equals(@NotNull T[] a1, @NotNull T[] a2, @NotNull Comparator<? super T> comparator) {
    //noinspection ArrayEquality
    if (a1 == a2) {
      return true;
    }
    int length = a2.length;
    if (a1.length != length) {
      return false;
    }

    for (int i = 0; i < length; i++) {
      if (comparator.compare(a1[i], a2[i]) != 0) {
        return false;
      }
    }
    return true;
  }

  @NotNull
  @Contract(pure=true)
  public static <T> T[] reverseArray(@NotNull T[] array) {
    T[] newArray = array.clone();
    for (int i = 0; i < array.length; i++) {
      newArray[array.length - i - 1] = array[i];
    }
    return newArray;
  }

  @NotNull
  @Contract(pure=true)
  public static int[] reverseArray(@NotNull int[] array) {
    int[] newArray = array.clone();
    for (int i = 0; i < array.length; i++) {
      newArray[array.length - i - 1] = array[i];
    }
    return newArray;
  }

  @Contract(pure=true)
  public static int lexicographicCompare(@NotNull String[] obj1, @NotNull String[] obj2) {
    for (int i = 0; i < Math.max(obj1.length, obj2.length); i++) {
      String o1 = i < obj1.length ? obj1[i] : null;
      String o2 = i < obj2.length ? obj2[i] : null;
      if (o1 == null) return -1;
      if (o2 == null) return 1;
      int res = o1.compareToIgnoreCase(o2);
      if (res != 0) return res;
    }
    return 0;
  }

  //must be Comparables
  @Contract(pure=true)
  public static <T> int lexicographicCompare(@NotNull T[] obj1, @NotNull T[] obj2) {
    for (int i = 0; i < Math.max(obj1.length, obj2.length); i++) {
      T o1 = i < obj1.length ? obj1[i] : null;
      T o2 = i < obj2.length ? obj2[i] : null;
      if (o1 == null) return -1;
      if (o2 == null) return 1;
      //noinspection unchecked
      int res = ((Comparable)o1).compareTo(o2);
      if (res != 0) return res;
    }
    return 0;
  }

  public static <T> void swap(@NotNull T[] array, int i1, int i2) {
    final T t = array[i1];
    array[i1] = array[i2];
    array[i2] = t;
  }

  public static void swap(@NotNull int[] array, int i1, int i2) {
    final int t = array[i1];
    array[i1] = array[i2];
    array[i2] = t;
  }

  public static void swap(@NotNull boolean[] array, int i1, int i2) {
    final boolean t = array[i1];
    array[i1] = array[i2];
    array[i2] = t;
  }

  public static void swap(@NotNull char[] array, int i1, int i2) {
    final char t = array[i1];
    array[i1] = array[i2];
    array[i2] = t;
  }

  public static <T> void rotateLeft(@NotNull T[] array, int i1, int i2) {
    final T t = array[i1];
    System.arraycopy(array, i1 + 1, array, i1, i2 - i1);
    array[i2] = t;
  }

  public static <T> void rotateRight(@NotNull T[] array, int i1, int i2) {
    final T t = array[i2];
    System.arraycopy(array, i1, array, i1 + 1, i2 - i1);
    array[i1] = t;
  }

  @Contract(pure=true)
  public static int indexOf(@NotNull Object[] objects, @Nullable Object object) {
    return indexOf(objects, object, 0, objects.length);
  }

  @Contract(pure=true)
  public static int indexOf(@NotNull Object[] objects, Object object, int start, int end) {
    if (object == null) {
      for (int i = start; i < end; i++) {
        if (objects[i] == null) return i;
      }
    }
    else {
      for (int i = start; i < end; i++) {
        if (object.equals(objects[i])) return i;
      }
    }
    return -1;
  }

  @Contract(pure=true)
  public static <T> int indexOf(@NotNull List<T> objects, T object, @NotNull Equality<T> comparator) {
    for (int i = 0; i < objects.size(); i++) {
      if (comparator.equals(objects.get(i), object)) return i;
    }
    return -1;
  }

  @Contract(pure=true)
  public static <T> int indexOf(@NotNull List<T> objects, T object, @NotNull Comparator<T> comparator) {
    for (int i = 0; i < objects.size(); i++) {
      if (comparator.compare(objects.get(i), object) == 0) return i;
    }
    return -1;
  }

  @Contract(pure=true)
  public static <T> int indexOf(@NotNull T[] objects, T object, @NotNull Equality<T> comparator) {
    for (int i = 0; i < objects.length; i++) {
      if (comparator.equals(objects[i], object)) return i;
    }
    return -1;
  }

  @Contract(pure=true)
  public static int indexOf(@NotNull long[] ints, long value) {
    for (int i = 0; i < ints.length; i++) {
      if (ints[i] == value) return i;
    }

    return -1;
  }
  @Contract(pure=true)
  public static int indexOf(@NotNull int[] ints, int value) {
    for (int i = 0; i < ints.length; i++) {
      if (ints[i] == value) return i;
    }

    return -1;
  }
  @Contract(pure=true)
  public static int indexOf(@NotNull short[] ints, short value) {
    for (int i = 0; i < ints.length; i++) {
      if (ints[i] == value) return i;
    }

    return -1;
  }

  @Contract(pure=true)
  public static int indexOf(@NotNull byte[] ints, byte value, int start, int end) {
    for (int i = start; i < end; i++) {
      if (ints[i] == value) return i;
    }

    return -1;
  }

  @Contract(pure=true)
  public static <T> int lastIndexOf(@NotNull final T[] src, final T obj) {
    for (int i = src.length - 1; i >= 0; i--) {
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

  @Contract(pure=true)
  public static int lastIndexOf(@NotNull final int[] src, final int obj) {
    for (int i = src.length - 1; i >= 0; i--) {
      final int o = src[i];
      if (o == obj) {
          return i;
      }
    }
    return -1;
  }

  @Contract(pure=true)
  public static <T> int lastIndexOf(@NotNull final T[] src, final T obj, @NotNull Equality<? super T> comparator) {
    for (int i = src.length - 1; i >= 0; i--) {
      final T o = src[i];
      if (comparator.equals(obj, o)) {
        return i;
      }
    }
    return -1;
  }

  @Contract(pure=true)
  public static <T> int lastIndexOf(@NotNull List<T> src, final T obj, @NotNull Equality<? super T> comparator) {
    for (int i = src.size() - 1; i >= 0; i--) {
      final T o = src.get(i);
      if (comparator.equals(obj, o)) {
        return i;
      }
    }
    return -1;
  }

  @Contract(pure=true)
  public static <T> boolean contains(@Nullable final T o, @NotNull T... objects) {
    return indexOf(objects, o) >= 0;
  }

  @Contract(pure=true)
  public static boolean contains(@Nullable final String s, @NotNull String... strings) {
    if (s == null) {
      for (String str : strings) {
        if (str == null) return true;
      }
    }
    else {
      for (String str : strings) {
        if (s.equals(str)) return true;
      }
    }

    return false;
  }

  @NotNull
  @Contract(pure=true)
  public static int[] newIntArray(int count) {
    return count == 0 ? EMPTY_INT_ARRAY : new int[count];
  }

  @NotNull
  @Contract(pure=true)
  public static long[] newLongArray(int count) {
    return count == 0 ? EMPTY_LONG_ARRAY : new long[count];
  }

  @NotNull
  @Contract(pure=true)
  public static String[] newStringArray(int count) {
    return count == 0 ? EMPTY_STRING_ARRAY : new String[count];
  }

  @NotNull
  @Contract(pure=true)
  public static Object[] newObjectArray(int count) {
    return count == 0 ? EMPTY_OBJECT_ARRAY : new Object[count];
  }

  @NotNull
  @Contract(pure=true)
  public static <E> E[] ensureExactSize(int count, @NotNull E[] sample) {
    if (count == sample.length) return sample;
    @SuppressWarnings("unchecked") final E[] array = createArray(sample.getClass().getComponentType(), count);
    return array;
  }

  @Nullable
  @Contract(value = "null -> null", pure=true)
  public static <T> T getFirstElement(@Nullable T[] array) {
    return array != null && array.length > 0 ? array[0] : null;
  }

  @Nullable
  @Contract(value = "null -> null", pure=true)
  public static <T> T getLastElement(@Nullable T[] array) {
    return array != null && array.length > 0 ? array[array.length - 1] : null;
  }

  @Contract(value = "null -> true", pure=true)
  public static <T> boolean isEmpty(@Nullable T[] array) {
    return array == null || array.length == 0;
  }

  @NotNull
  @Contract(pure=true)
  public static String[] toStringArray(@Nullable Collection<String> collection) {
    return ArrayUtilRt.toStringArray(collection);
  }

  public static <T> void copy(@NotNull final Collection<? extends T> src, @NotNull final T[] dst, final int dstOffset) {
    int i = dstOffset;
    for (T t : src) {
      dst[i++] = t;
    }
  }

  @Nullable
  @Contract("null -> null; !null -> !null")
  public static <T> T[] copyOf(@Nullable T[] original) {
    if (original == null) return null;
    return Arrays.copyOf(original, original.length);
  }

  @Nullable
  @Contract("null -> null; !null -> !null")
  public static boolean[] copyOf(@Nullable boolean[] original) {
    if (original == null) return null;
    return Arrays.copyOf(original, original.length);
  }

  @Nullable
  @Contract("null -> null; !null -> !null")
  public static int[] copyOf(@Nullable int[] original) {
    if (original == null) return null;
    return Arrays.copyOf(original, original.length);
  }

  @NotNull
  public static <T> T[] stripTrailingNulls(@NotNull T[] array) {
    return array.length != 0 && array[array.length-1] == null ? Arrays.copyOf(array, trailingNullsIndex(array)) : array;
  }

  private static <T> int trailingNullsIndex(@NotNull T[] array) {
    for (int i = array.length - 1; i >= 0; i--) {
      if (array[i] != null) {
        return i + 1;
      }
    }
    return 0;
  }

  // calculates average of the median values in the selected part of the array. E.g. for part=3 returns average in the middle third.
  public static long averageAmongMedians(@NotNull long[] time, int part) {
    assert part >= 1;
    int n = time.length;
    Arrays.sort(time);
    long total = 0;
    int start = n / 2 - n / part / 2;
    int end = n / 2 + n / part / 2;
    for (int i = start; i < end; i++) {
      total += time[i];
    }
    int middlePartLength = end - start;
    return middlePartLength == 0 ? 0 : total / middlePartLength;
  }

  public static long averageAmongMedians(@NotNull int[] time, int part) {
    assert part >= 1;
    int n = time.length;
    Arrays.sort(time);
    long total = 0;
    int start = n / 2 - n / part / 2;
    int end = n / 2 + n / part / 2;
    for (int i = start; i < end; i++) {
      total += time[i];
    }
    int middlePartLength = end - start;
    return middlePartLength == 0 ? 0 : total / middlePartLength;
  }

  public static int min(int[] values) {
    int min = Integer.MAX_VALUE;
    for (int value : values) {
      if (value < min) min = value;
    }
    return min;
  }
}
