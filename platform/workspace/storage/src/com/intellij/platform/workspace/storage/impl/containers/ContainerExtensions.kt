// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.workspace.storage.impl.containers

import com.intellij.util.containers.BidirectionalMultiMap

internal fun <A, B> BidirectionalMultiMap<A, B>.copy(): BidirectionalMultiMap<A, B> {
  val copy = BidirectionalMultiMap<A, B>()
  copy.putAll(this)
  return copy
}

internal fun <A, B> BidirectionalMultiMap<A, B>.putAll(another: BidirectionalMultiMap<A, B>) {
  another.keys.forEach { key -> another.getValues(key).forEach { value -> this.put(key, value) } }
}

internal fun <B> BidirectionalLongMultiMap<B>.putAll(another: BidirectionalLongMultiMap<B>) {
  another.keys.forEach { key -> another.getValues(key).forEach { value -> this.put(key, value) } }
}

internal fun <T> getDiff(beforeSetCopy: MutableSet<T>, after: Collection<T>): Pair<MutableSet<T>, ArrayList<T>> {
  val added = ArrayList<T>()
  after.forEach {
    val removed = beforeSetCopy.remove(it)
    if (!removed) {
      added += it
    }
  }
  // removed to added
  return beforeSetCopy to added
}