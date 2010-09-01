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
package com.intellij.util.containers;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.Stack;

/**
 * @author peter
 */
public class CollectionFactory {
  private CollectionFactory() {
  }

  public static <T> Set<T> newSet(T... elements) {
    return new HashSet<T>(Arrays.asList(elements));
  }

  public static <T> Set<T> newTroveSet(T... elements) {
    return newTroveSet(Arrays.asList(elements));
  }

  public static <T> Set<T> newTroveSet(Collection<T> elements) {
    return new THashSet<T>(elements);
  }

  public static <T> T[] ar(T... elements) {
    return elements;
  }

  public static <T,V> THashMap<T, V> newTroveMap() {
    return new THashMap<T,V>();
  }

  public static <T> ArrayList<T> arrayList() {
    return Lists.newArrayList();
  }

  public static <T> ArrayList<T> arrayList(T... elements) {
    return new ArrayList<T>(Arrays.asList(elements));
  }

  public static <T> List<T> arrayList(@NotNull final T[] elements, final int start, final int end) {
    if (start < 0 || start > end || end > elements.length) throw new IllegalArgumentException("start:" + start + " end:" + end + " length:" + elements.length);

    return new AbstractList<T>() {
      private final int size = end - start;

      @Override
      public T get(final int index) {
        if (index < 0 || index >= size) throw new IndexOutOfBoundsException("index:" + index + " size:" + size);
        return elements[start + index];
      }

      @Override
      public int size() {
        return size;
      }
    };
  }

  public static <T> Stack<T> stack() {
    return new Stack<T>();
  }

  public static <T> Set<T> hashSet() {
    return Sets.newHashSet();
  }

  public static <T, V> Map<T, V> hashMap() {
    return Maps.newHashMap();
  }

  public static <T, V> LinkedHashMap<T, V> linkedMap() {
    return Maps.newLinkedHashMap();
  }

  public static <T> LinkedHashSet<T> linkedHashSet() {
    return Sets.newLinkedHashSet();
  }
}
