// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers.prefixTree.set

import org.jetbrains.annotations.ApiStatus

@ApiStatus.NonExtendable
@ApiStatus.Internal
interface MutablePrefixTreeSet<Key> : PrefixTreeSet<Key> {

  fun add(element: Key)

  fun remove(element: Key)

  fun addAll(elements: Iterable<Key>): Unit =
    elements.forEach { add(it) }
}