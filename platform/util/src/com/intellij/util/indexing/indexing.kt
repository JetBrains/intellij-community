// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.util.indexing.impl.ValueContainerProcessor
import org.jetbrains.annotations.ApiStatus

/**
 * Helper functions for index access
 */
@ApiStatus.Internal
fun <K, V> InvertedIndex<K, V, *>.forEachValueOf(key: K & Any, action: ValueContainer.ContainerAction<V>) {
  withData(key, ValueContainerProcessor { container -> container.forEach(action) })
}

/** Less verbose call from kotlin */
@ApiStatus.Internal
fun <K, V> InvertedIndex<K, V, *>.withDataOf(key: K & Any, processor: (ValueContainer<V>) -> Boolean): Boolean {
  //Kotlin can't infer an exception type from java definition, hence need a verbose explicit type :(
  //Can't name method .withData() because kotlin can't choose the right overload by itself :(
  return withData(key, ValueContainerProcessor { container -> processor(container) })
}
