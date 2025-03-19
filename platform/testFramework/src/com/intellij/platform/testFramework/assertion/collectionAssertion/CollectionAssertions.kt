// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.assertion.collectionAssertion

import org.opentest4j.AssertionFailedError

object CollectionAssertions {

  @JvmStatic
  fun <T> assertEqualsOrdered(
    expected: Collection<T>?,
    actual: Collection<T>?,
    messageSupplier: (() -> String)? = null,
  ) {
    assertEqualsUnordered(expected, actual, messageSupplier)
    if (expected?.toList() != actual?.toList()) {
      throwAssertionFailedError(expected, actual, messageSupplier, """
        |Incorrect actual elements order.
      """.trimMargin())
    }
  }

  @JvmStatic
  fun <T> assertEqualsUnordered(
    expected: Collection<T>?,
    actual: Collection<T>?,
    messageSupplier: (() -> String)? = null,
  ) {
    if (expected == null && actual == null) {
      return
    }
    if (expected == null || actual == null) {
      throwAssertionFailedError(expected, actual, messageSupplier, """
        |Expecting actual:
        |  $actual
        |to match:
        |  $expected
        |but one of them is null.
      """.trimMargin())
    }
    doAssertEqualsUnordered(expected, actual, messageSupplier)
  }

  private fun <T> doAssertEqualsUnordered(
    expected: Collection<T>,
    actual: Collection<T>,
    messageSupplier: (() -> String)? = null,
  ) {
    val expectedSet = expected.toSet()
    val actualSet = actual.toSet()
    val notFound = expectedSet.minus(actualSet)
    val notExpected = actualSet.minus(expectedSet)

    if (notExpected.isNotEmpty() && notFound.isNotEmpty()) {
      throwAssertionFailedError(expected, actual, messageSupplier, """
        |Expecting actual:
        |  $actual
        |to contain exactly in any order:
        |  $expected
        |elements not found:
        |  $notFound
        |and elements not expected:
        |  $notExpected
      """.trimMargin())
    }
    if (notFound.isNotEmpty()) {
      throwAssertionFailedError(expected, actual, messageSupplier, """
        |Expecting actual:
        |  $actual
        |to contain exactly in any order:
        |  $expected
        |but could not find the following elements:
        |  $notFound
      """.trimMargin())
    }
    if (notExpected.isNotEmpty()) {
      throwAssertionFailedError(expected, actual, messageSupplier, """
        |Expecting actual:
        |  $actual
        |to contain exactly in any order:
        |  $expected
        |but the following elements were unexpected:
        |  $notExpected
      """.trimMargin())
    }
  }

  @JvmStatic
  fun <T> assertContainsUnordered(
    expected: Collection<T>,
    actual: Collection<T>,
    messageSupplier: (() -> String)? = null,
  ) {
    val expectedSet = expected.toSet()
    val actualSet = actual.toSet()
    val notFound = expectedSet.minus(actualSet)

    if (notFound.isNotEmpty()) {
      throwAssertionFailedError(expected, actual, messageSupplier, """
        |Expecting actual:
        |  $actual
        |to contain in any order:
        |  $expected
        |but could not find the following elements:
        |  $notFound
      """.trimMargin())
    }
  }

  @JvmStatic
  fun <T> assertEmpty(
    actual: Collection<T>,
    messageSupplier: (() -> String)? = null,
  ) {
    if (actual.isNotEmpty()) {
      throwAssertionFailedError(emptyList(), actual, messageSupplier, """
        |Expecting empty but was:
        |  $actual
      """.trimMargin())
    }
  }

  private fun <T> throwAssertionFailedError(
    expected: T,
    actual: T,
    messageSupplier: (() -> String)?,
    description: String,
  ): Nothing {
    val message = when (val message = messageSupplier?.invoke()) {
      null -> description
      else -> "$message\n$description"
    }
    throw AssertionFailedError(message, expected, actual)
  }
}
