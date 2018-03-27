// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.util

import com.intellij.util.containers.FlatteningIterator

/**
 * Creates read-only flat view of [collections].
 * Changes in underlying collections will be reflected in this view.
 */
fun <T> flatten(vararg collections: Collection<T>): Collection<T> = FlatCollection(collections)

private class FlatCollection<T>(private val collections: Array<out Collection<T>>) : AbstractCollection<T>() {

  override val size: Int get() = collections.sumBy { it.size }

  override fun iterator(): Iterator<T> = object : FlatteningIterator<Collection<T>, T>(collections.iterator()) {

    override fun createValueIterator(group: Collection<T>): Iterator<T> = group.iterator()
  }
}
