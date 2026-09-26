// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.assertion.collectionAssertion

interface CollectionAssertion<T> {

  /** Registers an assertion block for the next element in the collection. */
  fun assertElement(assert: (T) -> Unit)

  companion object {

    /**
     * Asserts that [actual] contains exactly the items described by the [configure] block, in order.
     *
     * Each call to [CollectionAssertion.assertElement] registers an assertion for the next element.
     * After all items are registered the builder runs all assertions and reports the index of any failure.
     */
    fun <T> assertCollectionOrdered(actual: Collection<T>, configure: CollectionAssertion<T>.() -> Unit) {
      CollectionAssertionImpl<T>()
        .apply(configure)
        .assertCollection(actual)
    }
  }
}