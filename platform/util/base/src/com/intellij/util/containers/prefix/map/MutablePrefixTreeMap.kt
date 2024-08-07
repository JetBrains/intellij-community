// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers.prefix.map

import org.jetbrains.annotations.ApiStatus

/**
 * Mutable interface for [PrefixTreeMap].
 *
 * @see PrefixTreeMap
 */
@ApiStatus.NonExtendable
@ApiStatus.Internal
interface MutablePrefixTreeMap<Key, Value> : PrefixTreeMap<Key, Value> {

  operator fun set(key: Key, value: Value): Value?

  fun remove(key: Key): Value?
}
