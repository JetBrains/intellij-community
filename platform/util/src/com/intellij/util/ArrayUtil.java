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

import com.intellij.openapi.util.Comparing;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayCharSequence;
import gnu.trove.Equality;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Author: msk
 */
public class ArrayUtil {
  public static final short[] EMPTY_SHORT_ARRAY = new short[0];
  public static final char[] EMPTY_CHAR_ARRAY = new char[0];
  public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
  public static final int [] EMPTY_INT_ARRAY = new int[0];
  public static final boolean[] EMPTY_BOOLEAN_ARRAY = new boolean[0];
  @SuppressWarnings({"SSBasedInspection"}) public static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];
  @SuppressWarnings({"SSBasedInspection"}) public static final String[] EMPTY_STRING_ARRAY = new String[0];
  @SuppressWarnings({"SSBasedInspection"}) public static final Class[] EMPTY_CLASS_ARRAY = new Class[0];
  public static final long[] EMPTY_LONG_ARRAY = new long[0];
  public static final Collection[] EMPTY_COLLECTION_ARRAY = new Collection[0];
  public static final CharSequence EMPTY_CHAR_SEQUENCE = new CharArrayCharSequence(EMPTY_CHAR_ARRAY);

  @NotNull
  public static byte[] realloc (@NotNull byte [] array, final int newSize) {
    if (newSize == 0) {
      return EMPTY_BYTE_ARRAY;
    }

    final int oldSize = array.length;
    if (oldSize == newSize) {
      return array;
    }

    final byte [] result = new byte [newSize];
    System.arraycopy(array, 0, result, 0, Math.min (oldSize, newSize));
    return result;
  }

  @NotNull
  public static int[] realloc (@NotNull int [] array, final int newSize) {
    if (newSize == 0) {
      return EMPTY_INT_ARRAY;
    }

    final int oldSize = array.length;
    if (oldSize == newSize) {
      return array;
    }

    final int [] result = new int [newSize];
    System.arraycopy(array, 0, result, 0, Math.min (oldSize, newSize));
    return result;
  }

  @NotNull
  public static int[] append(@NotNull int[] array, int value) {
    array = realloc(array, array.length + 1);
    array[array.length - 1] = value;
    return array;
  }

  @NotNull
  public static char[] realloc (@NotNull char[] array, final int newSize) {
    if (newSize == 0) {
      return EMPTY_CHAR_ARRAY;
    }

    final int oldSize = array.length;
    if (oldSize == newSize) {
      return array;
    }

    final char[] result = new char[newSize];
    System.arraycopy(array, 0, result, 0, Math.min (oldSize, newSize));
    return result;
  }

  @NotNull
  public static<T> T[] toObjectArray(@NotNull Collection<T> collection, @NotNull Class<T> aClass) {
    T[] array = (T[]) Array.newInstance(aClass, collection.size());
    return collection.toArray(array);
  }

  @NotNull
  public static<T> T[] toObjectArray(@NotNull Class<T> aClass, Object... source) {
    T[] array = (T[]) Array.newInstance(aClass, source.length);
    System.arraycopy(source, 0, array, 0, array.length);
    return array;
  }

  @NotNull
  public static Object[] toObjectArray(@NotNull Collection<?> collection) {
    if (collection.isEmpty()) return EMPTY_OBJECT_ARRAY;
    //noinspection SSBasedInspection
    return collection.toArray(new Object[collection.size()]);
  }

  @NotNull
  public static String[] toStringArray(@NotNull Collection<String> collection) {
    if (collection.isEmpty()) return EMPTY_STRING_ARRAY;
    return ContainerUtil.toArray(collection, new String[collection.size()]);
  }

  @NotNull
  public static <T> T[] mergeArrays(@NotNull T[] a1, @NotNull T[] a2, @NotNull Class<T> aClass) {
    if (a1.length == 0) {
      return a2;
    }
    if (a2.length == 0) {
      return a1;
    }
    T[] highlights =(T[])Array.newInstance(aClass, a1.length + a2.length);
    System.arraycopy(a1, 0, highlights, 0, a1.length);
    System.arraycopy(a2, 0, highlights, a1.length, a2.length);
    return highlights;
  }


  @NotNull
  public static int[] mergeArrays(@NotNull int[] a1, @NotNull int[] a2) {
    if (a1.length == 0) {
      return a2;
    }
    if (a2.length == 0) {
      return a1;
    }
    int[] a = new int[a1.length + a2.length];
    int idx = 0;
    for (int i : a1) {
      a[idx++] = i;
    }
    for (int i : a2) {
      a[idx++] = i;
    }
    return a;
  }

  /**
   * Allocates new array of size <code>array.length + collection.size()</code> and copies elements of <code>array</code> and
   * <code>collection</code> to it.
   * @param array source array
   * @param collection source collection
   * @param factory array factory used to create destination array of type <code>T</code>
   * @return destination array
   */
  @NotNull
  public static <T> T[] mergeArrayAndCollection(@NotNull T[] array,
                                                @NotNull Collection<T> collection,
                                                @NotNull final ArrayFactory<T> factory) {
    if (collection.isEmpty()) {
      return array;
    }

    final T[] array2 = collection.toArray(factory.create(collection.size()));
    if (array.length == 0) {
      return array2;
    }

    final T[] result = factory.create(array.length + collection.size());
    System.arraycopy(array, 0, result, 0, array.length);
    System.arraycopy(array2, 0, result, array.length, array2.length);
    return result;
  }

  /**
   * Appends <code>element</code> to the <code>src</code> array. As you can
   * imagine the appended element will be the last one in the returned result.
   * @param src array to which the <code>element</code> should be appended.
   * @param element object to be appended to the end of <code>src</code> array.
   * @return new array
   */
  @NotNull
  public static <T> T[] append(@NotNull final T[] src,final T element){
    return append(src, element, (Class<T>)src.getClass().getComponentType());
  }

  public static <T> T[] append(@NotNull final T[] src,final T element, ArrayFactory<T> factory) {
    int length=src.length;
    T[] result= factory.create(length + 1);
    System.arraycopy(src, 0, result, 0, length);
    result[length] = element;
    return result;
  }

  @NotNull
  public static <T> T[] append(@NotNull T[] src, final T element, @NotNull Class<T> componentType) {
    int length=src.length;
    T[] result=(T[])Array.newInstance(componentType, length+ 1);
    System.arraycopy(src, 0, result, 0, length);
    result[length] = element;
    return result;
  }

  /**
   * Removes element with index <code>idx</code> from array <code>src</code>.
   * @param src array.
   * @param idx index of element to be removed.
   * @return modified array.
   */
  @NotNull
  public static <T> T[] remove(@NotNull final T[] src,int idx){
    int length=src.length;
    if (idx < 0 || idx >= length) {
      throw new IllegalArgumentException("invalid index: " + idx);
    }
    T[] result=(T[])Array.newInstance(src.getClass().getComponentType(), length-1);
    System.arraycopy(src, 0, result, 0, idx);
    System.arraycopy(src, idx+1, result, idx, length-idx-1);
    return result;
  }

  @NotNull
  public static <T> T[] remove(@NotNull final T[] src,int idx, ArrayFactory<T> factory) {
    int length=src.length;
    if (idx < 0 || idx >= length) {
      throw new IllegalArgumentException("invalid index: " + idx);
    }
    T[] result=factory.create(length-1);
    System.arraycopy(src, 0, result, 0, idx);
    System.arraycopy(src, idx+1, result, idx, length-idx-1);
    return result;
  }

  @NotNull
  public static <T> T[] remove(@NotNull final T[] src, T element) {
    final int idx = find(src, element);
    if (idx == -1) return src;

    return remove(src, idx);
  }

  @NotNull
  public static <T> T[] remove(@NotNull final T[] src, T element, ArrayFactory<T> factory) {
    final int idx = find(src, element);
    if (idx == -1) return src;

    return remove(src, idx, factory);
  }

  @NotNull
  public static int[] remove(@NotNull final int[] src,int idx){
    int length=src.length;
    if (idx < 0 || idx >= length) {
      throw new IllegalArgumentException("invalid index: " + idx);
    }
    int[] result = new int[src.length - 1];
    System.arraycopy(src, 0, result, 0, idx);
    System.arraycopy(src, idx+1, result, idx, length-idx-1);
    return result;
  }

  /**
   * @param src source array.
   * @param obj object to be found.
   * @return index of <code>obj</code> in the <code>src</code> array.
   * Returns <code>-1</code> if passed object isn't found. This method uses
   * <code>equals</code> of arrays elements to compare <code>obj</code> with
   * these elements.
   */
  public static <T> int find(@NotNull final T[] src,final T obj){
    for(int i=0;i<src.length;i++){
      final T o=src[i];
      if(o==null){
        if(obj==null){
          return i;
        }
      }else{
        if(o.equals(obj)){
          return i;
        }
      }
    }
    return -1;
  }
  public static <T> int lastIndexOf(@NotNull final T[] src,final T obj){
    for(int i=src.length-1;i>=0;i--){
      final T o=src[i];
      if(o==null){
        if(obj==null){
          return i;
        }
      }else{
        if(o.equals(obj)){
          return i;
        }
      }
    }
    return -1;
  }

  public static int find(@NotNull int[] src, int obj) {
    return indexOf(src, obj);
  }

  public static boolean startsWith(byte[] array, byte[] subArray) {
    if (array == subArray) {
      return true;
    }
    if (array == null || subArray == null) {
      return false;
    }

    int length = subArray.length;
    if (array.length < length) {
      return false;
    }

    for (int i = 0; i < length; i++) {
      if (array[i] != subArray[i]) {
        return false;
      }
    }

    return true;
  }

  public static <E> boolean startsWith(E[] array, E[] subArray) {
    if (array == subArray) {
      return true;
    }
    if (array == null || subArray == null) {
      return false;
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

  public static <T> boolean equals(T[] a1, T[] a2, Equality<? super T> comparator) {
    if (a1 == a2) {
      return true;
    }
    if (a1 == null || a2 == null) {
      return false;
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

  public static <T> boolean equals(T[] a1, T[] a2, Comparator<? super T> comparator) {
    if (a1 == a2) {
      return true;
    }
    if (a1 == null || a2 == null) {
      return false;
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
  public static <T> T[] reverseArray(@NotNull T[] array) {
    T[] newArray = array.clone();
    for (int i = 0; i < array.length; i++) {
      newArray[array.length - i - 1] = array[i];
    }
    return newArray;
  }

  @NotNull
  public static int[] reverseArray(@NotNull int[] array) {
    int[] newArray = array.clone();
    for (int i = 0; i < array.length; i++) {
      newArray[array.length - i - 1] = array[i];
    }
    return newArray;
  }

  public static void reverse(@NotNull char[] array) {
    for (int i = 0; i < array.length; i++) {
      swap(array, array.length - i - 1, i);
    }
  }

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
  public static <T> int lexicographicCompare(@NotNull T[] obj1, @NotNull T[] obj2) {
    for (int i = 0; i < Math.max(obj1.length, obj2.length); i++) {
      T o1 = i < obj1.length ? obj1[i] : null;
      T o2 = i < obj2.length ? obj2[i] : null;
      if (o1 == null) return -1;
      if (o2 == null) return 1;
      int res = ((Comparable)o1).compareTo(o2);
      if (res != 0) return res;
    }
    return 0;
  }

  public static <T>void swap(@NotNull T[] array, int i1, int i2) {
    final T t = array[i1];
    array[i1] = array[i2];
    array[i2] = t;
  }

  public static void swap(@NotNull int[] array, int i1, int i2) {
    final int t = array[i1];
    array[i1] = array[i2];
    array[i2] = t;
  }
  public static void swap(@NotNull boolean [] array, int i1, int i2) {
    final boolean t = array[i1];
    array[i1] = array[i2];
    array[i2] = t;
  }
  public static void swap(@NotNull char[] array, int i1, int i2) {
    final char t = array[i1];
    array[i1] = array[i2];
    array[i2] = t;
  }

  public static <T>void rotateLeft(@NotNull T[] array, int i1, int i2) {
    final T t = array[i1];
    System.arraycopy(array, i1 + 1, array, i1, i2-i1);
    array[i2] = t;
  }
  public static <T>void rotateRight(@NotNull T[] array, int i1, int i2) {
    final T t = array[i2];
    System.arraycopy(array, i1, array, i1+1, i2-i1);
    array[i1] = t;
  }

  public static int indexOf(@NotNull Object[] objects, Object object) {
    for (int i = 0; i < objects.length; i++) {
      if (Comparing.equal(objects[i], object)) return i;
    }
    return -1;
  }

  public static <T> int indexOf(@NotNull List<T> objects, T object, @NotNull Equality<T> comparator) {
    for (int i = 0; i < objects.size(); i++) {
      if (comparator.equals(objects.get(i), object)) return i;
    }
    return -1;
  }
  public static <T> int indexOf(@NotNull List<T> objects, T object, @NotNull Comparator<T> comparator) {
    for (int i = 0; i < objects.size(); i++) {
      if (comparator.compare(objects.get(i), object) == 0) return i;
    }
    return -1;
  }
  public static <T> int indexOf(@NotNull T[] objects, T object, @NotNull Equality<T> comparator) {
    for (int i = 0; i < objects.length; i++) {
      if (comparator.equals(objects[i], object)) return i;
    }
    return -1;
  }

  public static int indexOf(@NotNull int[] ints, int value) {
    for (int i = 0; i < ints.length; i++) {
      if (ints[i] == value) return i;
    }

    return -1;
  }

  public static boolean contains(final Object o, final Object... objects) {
    return indexOf(objects, o) >= 0;
  }

  public static int[] newIntArray(int count) {
    return count == 0 ? EMPTY_INT_ARRAY : new int[count];
  }
  public static String[] newStringArray(int count) {
    return count == 0 ? EMPTY_STRING_ARRAY : new String[count];
  }

  @NotNull
  public static <E> E[] ensureExactSize(int count, @NotNull E[] sample) {
    if (count == sample.length) return sample;

    return (E[])Array.newInstance(sample.getClass().getComponentType(), count);
  }
}
