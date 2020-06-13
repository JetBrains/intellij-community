// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl.containers

import com.intellij.util.containers.BidirectionalMap
import com.intellij.util.containers.BidirectionalMultiMap

internal fun <A, B> BidirectionalMultiMap<A, B>.copy(): BidirectionalMultiMap<A, B> {
  val copy = BidirectionalMultiMap<A, B>()
  copy.putAll(this)
  return copy
}

internal fun <A, B> BidirectionalMultiMap<A, B>.putAll(another: BidirectionalMultiMap<A, B>) {
  another.keys.forEach { key -> another.getValues(key).forEach { value -> this.put(key, value) } }
}

internal fun <A, B> BidirectionalMap<A, B>.copy(): BidirectionalMap<A, B> {
  val copy = BidirectionalMap<A, B>()
  keys.forEach { key -> this[key]?.also { value -> copy[key] = value } }
  return copy
}

