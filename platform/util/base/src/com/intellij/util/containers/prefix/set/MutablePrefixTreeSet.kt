// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers.prefix.set

import org.jetbrains.annotations.ApiStatus

@ApiStatus.NonExtendable
@ApiStatus.Internal
interface MutablePrefixTreeSet<Key> : PrefixTreeSet<Key> {

  fun add(element: Key)

  fun remove(element: Key)
}