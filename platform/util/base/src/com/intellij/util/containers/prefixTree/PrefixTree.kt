// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers.prefixTree

import com.intellij.util.containers.prefixTree.map.PrefixTreeMap
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@ApiStatus.NonExtendable
interface PrefixTree<Key, Value> : PrefixTreeMap<List<Key>, Value>