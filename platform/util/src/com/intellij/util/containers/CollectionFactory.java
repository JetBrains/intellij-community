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

import gnu.trove.THashMap;
import gnu.trove.THashSet;

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
    return new ArrayList<T>();
  }

  public static <T> Stack<T> stack() {
    return new Stack<T>();
  }

  public static <T> HashSet<T> hashSet() {
    return new HashSet<T>();
  }

  public static <T, V> LinkedHashMap<T, V> linkedMap() {
    return new LinkedHashMap<T,V>();
  }
}
