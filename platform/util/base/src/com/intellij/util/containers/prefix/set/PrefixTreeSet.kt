// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers.prefix.set

import com.intellij.util.containers.prefix.factory.PrefixTreeFactory
import org.jetbrains.annotations.ApiStatus

/**
 * Map for fast finding elements by their prefix.
 * [Key] is an element which can be represented as a sequence of objects.
 *
 * @see MutablePrefixTreeSet
 * @see PrefixTreeFactory
 * @see com.intellij.util.containers.prefix.map.PrefixTreeMap
 */
@ApiStatus.NonExtendable
@ApiStatus.Internal
interface PrefixTreeSet<Key> : Set<Key> {

  /**
   * Returns descendant elements for [element].
   *
   * For example, we have a set of `[a,b,c]`, `[a,b,c,d]`, `[a,b,c,e]` and `[a,f,g]`.
   * Then ancestor elements for [element]`=[a,b]` are `[a,b,c]`, `[a,b,c,d]` and `[a,b,c,e]`.
   */
  fun getDescendants(element: Key): Set<Key>

  /**
   * Returns ancestor elements for [element].
   *
   * For example, we have a set of `[a,b,c]`, `[a,b,c,d]`, `[a,b,c,e]` and `[a,f,g]`.
   * Then descendant elements for [element]`=[a,b,c,d,e]` are `[a,b,c]` and `[a,b,c,d]`.
   */
  fun getAncestors(element: Key): Set<Key>

  /**
   * Returns root elements in this set.
   *
   * For example, we have a set of `[a,b,c]`, `[a,b,c,d]`, `[a,b,c,e]` and `[a,f,g]`.
   * Then root elements are `[a,b,c]` and `[a,f,g]`.
   */
  fun getRoots(): Set<Key>
}