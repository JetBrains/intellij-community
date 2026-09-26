// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.assertion.collectionAssertion

import org.opentest4j.AssertionFailedError

internal class CollectionAssertionImpl<T> : CollectionAssertion<T> {

  private val elementAssertions = mutableListOf<(T) -> Unit>()

  override fun assertElement(assert: (T) -> Unit) {
    elementAssertions.add(assert)
  }

  fun assertCollection(actual: Collection<T>) {
    if (elementAssertions.size != actual.size) {
      val errorMessage = """
        |Collection size assertion failure:
        | actual=$actual
      """.trimMargin()
      throw AssertionFailedError(errorMessage, elementAssertions.size, actual.size)
    }
    elementAssertions.zip(actual)
      .forEachIndexed { index, (elementAssertion, element) ->
        try {
          elementAssertion.invoke(element)
        }
        catch (e: AssertionError) {
          val errorMessage = """
            |Collection element assertion failure:
            | index=$index
            | element=$element
            | actual=$actual
          """.trimMargin()
          throw AssertionError(errorMessage, e)
        }
      }
  }
}