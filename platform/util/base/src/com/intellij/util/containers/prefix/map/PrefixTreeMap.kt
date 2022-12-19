// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers.prefix.map

import org.jetbrains.annotations.ApiStatus

/**
 * @see com.intellij.util.containers.prefix.set.PrefixTreeSet
 */
@ApiStatus.NonExtendable
interface PrefixTreeMap<Key, Value> : Map<Key, Value> {

  fun getKeySequence(): Sequence<Key>

  fun getValueSequence(): Sequence<Value>

  fun getEntrySequence(): Sequence<Pair<Key, Value>>

  /**
   * Returns descendant keys for [key].
   *
   * For example, we have a map of `[a,b,c]`, `[a,b,c,d]`, `[a,b,c,e]` and `[a,f,g]`.
   * Then ancestor keys for [key]`=[a,b]` are `[a,b,c]`, `[a,b,c,d]` and `[a,b,c,e]`.
   */
  fun getDescendantKeys(key: Key): Set<Key>

  fun getDescendantValues(key: Key): List<Value>

  fun getDescendantEntries(key: Key): Map<Key, Value>

  fun getDescendantKeySequence(key: Key): Sequence<Key>

  fun getDescendantValueSequence(key: Key): Sequence<Value>

  fun getDescendantEntrySequence(key: Key): Sequence<Pair<Key, Value>>

  /**
   * Returns ancestor elements for [key].
   *
   * For example, we have a map of `[a,b,c]`, `[a,b,c,d]`, `[a,b,c,e]` and `[a,f,g]`.
   * Then descendant keys for [key]`=[a,b,c,d,e]` are `[a,b,c]` and `[a,b,c,d]`.
   */
  fun getAncestorKeys(key: Key): Set<Key>

  fun getAncestorValues(key: Key): List<Value>

  fun getAncestorEntries(key: Key): Map<Key, Value>

  fun getAncestorKeySequence(key: Key): Sequence<Key>

  fun getAncestorValueSequence(key: Key): Sequence<Value>

  fun getAncestorEntrySequence(key: Key): Sequence<Pair<Key, Value>>

  /**
   * Returns root keys in this map.
   *
   * For example, we have a map of `[a,b,c]`, `[a,b,c,d]`, `[a,b,c,e]` and `[a,f,g]`.
   * Then root keys are `[a,b,c]` and `[a,f,g]`.
   */
  fun getRootKeys(): Set<Key>

  fun getRootValues(): List<Value>

  fun getRootEntries(): Map<Key, Value>

  fun getRootKeySequence(): Sequence<Key>

  fun getRootValueSequence(): Sequence<Value>

  fun getRootEntrySequence(): Sequence<Pair<Key, Value>>
}