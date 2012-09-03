/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Stack;

/**
 * @author peter
 * @deprecated please use corresponding methods of {@linkplain ContainerUtil} class (to remove in IDEA 13).
 */
@SuppressWarnings("UnusedDeclaration")
public class CollectionFactory {
  private CollectionFactory() { }

  @NotNull
  public static <T> HashSet<T> hashSet() {
    return ContainerUtil.newHashSet();
  }

  @NotNull
  public static <T> HashSet<T> hashSet(@NotNull Collection<T> elements) {
    return ContainerUtil.newHashSet(elements);
  }

  @NotNull
  public static <T> HashSet<T> hashSet(@NotNull T... elements) {
    return ContainerUtil.newHashSet(elements);
  }

  @NotNull
  public static <T> LinkedHashSet<T> linkedHashSet() {
    return ContainerUtil.newLinkedHashSet();
  }

  @NotNull
  public static <T> LinkedHashSet<T> linkedHashSet(@NotNull Collection<T> elements) {
    return ContainerUtil.newLinkedHashSet(elements);
  }

  @NotNull
  public static <T> LinkedHashSet<T> linkedHashSet(@NotNull T... elements) {
    return ContainerUtil.newLinkedHashSet(elements);
  }

  @NotNull
  public static <T> THashSet<T> troveSet(@NotNull T... elements) {
    return ContainerUtil.newTroveSet(elements);
  }

  @NotNull
  public static <T> THashSet<T> troveSet(@NotNull Collection<T> elements) {
    return ContainerUtil.newTroveSet(elements);
  }

  @NotNull
  public static <T> TreeSet<T> treeSet() {
    return ContainerUtil.newTreeSet();
  }

  @NotNull
  public static <T> TreeSet<T> treeSet(@NotNull Collection<T> elements) {
    return ContainerUtil.newTreeSet(elements);
  }

  @NotNull
  public static <T> TreeSet<T> treeSet(@NotNull T... elements) {
    return ContainerUtil.newTreeSet(Arrays.asList(elements));
  }

  @NotNull
  public static <T> TreeSet<T> treeSet(@NotNull Comparator<? super T> comparator) {
    return ContainerUtil.newTreeSet(comparator);
  }

  @NotNull
  public static <T> Set<T> unmodifiableHashSet(@NotNull T... elements) {
    return ContainerUtil.immutableSet(elements);
  }

  @NotNull
  public static <T> Set<T> unmodifiableHashSet(@NotNull Collection<T> elements) {
    return Collections.unmodifiableSet(ContainerUtil.newHashSet(elements));
  }

  @NotNull
  public static <K, V> HashMap<K, V> hashMap() {
    return (HashMap<K, V>)ContainerUtil.newHashMap();
  }

  @NotNull
  public static <K, V> HashMap<K, V> hashMap(@NotNull Map<K, V> map) {
    return (HashMap<K, V>)ContainerUtil.newHashMap(map);
  }

  @NotNull
  public static <K, V> TreeMap<K, V> treeMap() {
    return new TreeMap<K, V>();
  }

  @NotNull
  public static <K, V> THashMap<K, V> troveMap() {
    return ContainerUtil.newTroveMap();
  }

  @NotNull
  public static <K, V> IdentityHashMap<K, V> identityHashMap() {
    return ContainerUtil.newIdentityHashMap();
  }

  @NotNull
  public static <K> THashSet<K> identityTroveSet() {
    return ContainerUtil.newIdentityTroveSet();
  }

  @NotNull
  public static <K> THashSet<K> identityTroveSet(int initialCapacity) {
    return ContainerUtil.newIdentityTroveSet(initialCapacity);
  }

  @NotNull
  public static <K, V> LinkedHashMap<K, V> linkedHashMap() {
    return ContainerUtil.newLinkedHashMap();
  }

  @NotNull
  public static <K extends Enum<K>, V> EnumMap<K, V> enumMap(@NotNull Class<K> klass) {
    return new EnumMap<K, V>(klass);
  }

  @NotNull
  public static <K extends Enum<K>, V> EnumMap<K, V> enumMap(@NotNull Map<K, V> map) {
    return new EnumMap<K, V>(map);
  }

  @NotNull
  public static <K, V> Map<K, V> hashMap(@NotNull List<K> keys, @NotNull List<V> values) {
    return ContainerUtil.newHashMap(keys, values);
  }

  @NotNull
  public static <T> ArrayList<T> arrayList() {
    return ContainerUtil.newArrayList();
  }

  @NotNull
  public static <T> ArrayList<T> arrayList(int initialCapacity) {
    return ContainerUtil.newArrayListWithCapacity(initialCapacity);
  }

  @NotNull
  public static <T> ArrayList<T> arrayList(@NotNull Collection<T> elements) {
    return ContainerUtil.newArrayList(elements);
  }

  @NotNull
  public static <T> ArrayList<T> arrayList(T... elements) {
    return ContainerUtil.newArrayList(elements);
  }

  @NotNull
  public static <T> ArrayList<T> arrayList(@NotNull Iterable<T> elements) {
    return ContainerUtil.newArrayList(elements);
  }

  @NotNull
  public static <T> List<T> arrayList(@NotNull final T[] elements, final int start, final int end) {
    return ContainerUtil.newArrayList(elements, start, end);
  }

  @NotNull
  public static <T> LinkedList<T> linkedList() {
    return ContainerUtil.newLinkedList();
  }

  @NotNull
  public static <T> LinkedList<T> linkedList(@NotNull Collection<T> elements) {
    return ContainerUtil.newLinkedList(elements);
  }

  @NotNull
  public static <T> LinkedList<T> linkedList(T... elements) {
    return ContainerUtil.newLinkedList(elements);
  }

  @NotNull
  public static <T> LinkedList<T> linkedList(@NotNull Iterable<T> elements) {
    return ContainerUtil.newLinkedList(elements);
  }

  @NotNull
  public static <T> Stack<T> stack() {
    return new Stack<T>();
  }

  @NotNull
  public static <T> T[] ar(@NotNull T... elements) {
    return elements;
  }
}
