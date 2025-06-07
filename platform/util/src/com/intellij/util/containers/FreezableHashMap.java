// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A (mutable) {@link Map} which, after calling {@link #freeze()}, becomes unmodifiable.
 * Useful when you want to create and fill an {@link java.util.HashMap} and then return it as-is with a "no mutations" guarantee.
 */
class FreezableHashMap<K,V> extends HashMap<K,V> {
  private boolean isFrozen;

  FreezableHashMap(int initialCapacity) {
    super(initialCapacity);
  }

  FreezableHashMap() {
  }

  FreezableHashMap(@NotNull Map<? extends K, ? extends V> c) {
    super(c);
  }

  @NotNull
  @Unmodifiable
  Map<K,V> freeze() {
    isFrozen = true;
    return this;
  }
  private void checkForFrozen() {
    if (isFrozen()) {
      throw new UnsupportedOperationException("This map is unmodifiable");
    }
  }

  @Override
  public V put(K key, V value) {
    checkForFrozen();
    return super.put(key, value);
  }

  @Override
  public void putAll(Map<? extends K, ? extends V> m) {
    checkForFrozen();
    super.putAll(m);
  }

  @Override
  public V remove(Object key) {
    checkForFrozen();
    return super.remove(key);
  }

  @Override
  public void clear() {
    checkForFrozen();
    super.clear();
  }

  @Override
  public boolean remove(Object key, Object value) {
    checkForFrozen();
    return super.remove(key, value);
  }

  @Override
  public boolean replace(K key, V oldValue, V newValue) {
    checkForFrozen();
    return super.replace(key, oldValue, newValue);
  }

  @Override
  public V replace(K key, V value) {
    checkForFrozen();
    return super.replace(key, value);
  }

  @Override
  public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
    checkForFrozen();
    return super.computeIfAbsent(key, mappingFunction);
  }

  @Override
  public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
    checkForFrozen();
    return super.computeIfPresent(key, remappingFunction);
  }

  @Override
  public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
    checkForFrozen();
    return super.compute(key, remappingFunction);
  }

  @Override
  public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
    checkForFrozen();
    return super.merge(key, value, remappingFunction);
  }

  @Override
  public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
    checkForFrozen();
    super.replaceAll(function);
  }

  @Override
  public V putIfAbsent(K key, V value) {
    checkForFrozen();
    return super.putIfAbsent(key, value);
  }

  private boolean isFrozen() {
    return isFrozen;
  }

  @Override
  public @NotNull Set<K> keySet() {
    return isFrozen() ? Collections.unmodifiableSet(super.keySet()) : super.keySet();
  }

  @Override
  public @NotNull Collection<V> values() {
    return isFrozen() ? Collections.unmodifiableCollection(super.values()) : super.values();
  }

  @Override
  public @NotNull Set<Entry<K, V>> entrySet() {
    return isFrozen() ? Collections.unmodifiableSet(super.entrySet()) : super.entrySet();
  }
}
