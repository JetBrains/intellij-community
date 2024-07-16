// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.testFramework.utils.module

import org.jetbrains.annotations.ApiStatus
import org.opentest4j.AssertionFailedError


fun <T> assertEqualsUnordered(expected: Collection<T>, actual: Collection<T>) {
  val expectedSet = expected.toSet()
  val actualSet = actual.toSet()
  val notFound = expectedSet.minus(actualSet)
  val notExpected = actualSet.minus(expectedSet)

  if (notExpected.isNotEmpty() && notFound.isNotEmpty()) {
    val message = """|
      |Expecting actual:
      |  $actual
      |to contain exactly in any order:
      |  $expected
      |elements not found:
      |  $notFound
      |and elements not expected:
      |  $notExpected
    """.trimMargin()
    throw AssertionFailedError(message, expected, actual)
  }
  if (notFound.isNotEmpty()) {
    val message = """|
      |Expecting actual:
      |  $actual
      |to contain exactly in any order:
      |  $expected
      |but could not find the following elements:
      |  $notFound
    """.trimMargin()
    throw AssertionFailedError(message, expected, actual)
  }
  if (notExpected.isNotEmpty()) {
    val message = """|
      |Expecting actual:
      |  $actual
      |to contain exactly in any order:
      |  $expected
      |but the following elements were unexpected:
      |  $notExpected
    """.trimMargin()
    throw AssertionFailedError(message, expected, actual)
  }
}

fun <T> assertContains(expected: Collection<T>, actual: Collection<T>) {
  val expectedSet = expected.toSet()
  val actualSet = actual.toSet()
  val notFound = expectedSet.minus(actualSet)

  if (notFound.isNotEmpty()) {
    val message = """|
      |Expecting actual:
      |  $actual
      |to contain in any order:
      |  $expected
      |but could not find the following elements:
      |  $notFound
    """.trimMargin()
    throw AssertionFailedError(message, expected, actual)
  }
}
