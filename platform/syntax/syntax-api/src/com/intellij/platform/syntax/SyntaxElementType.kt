// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalAtomicApi::class)
@file:ApiStatus.Experimental

package com.intellij.platform.syntax

import org.jetbrains.annotations.ApiStatus
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndIncrement
import kotlin.jvm.JvmOverloads

/**
 * A class defining a token or node type.
 *
 * [debugName] is used only for debug purposes.
 */
@ApiStatus.Experimental
class SyntaxElementType internal constructor(
  private val debugName: String,
  internal val lazyParser: LazyParser?,
  @Suppress("unused") unusedParam: Any?, // this parameter is necessary for disambiguation with the factory function
) {
  val index: Int = counter.fetchAndIncrement()

  /**
   * Checks if this element type is lazy-parseable.
   * For performing reparse, use [parseLazyNode] and [canLazyNodeBeReparsedIncrementally] functions.
   *
   * @return `true` if this element type is lazy-parseable.
   */
  fun isLazyParseable(): Boolean = lazyParser != null

  override fun toString(): String = debugName

  override fun equals(other: Any?): Boolean = this === other

  override fun hashCode(): Int = index
}

@ApiStatus.Experimental
@JvmOverloads
fun SyntaxElementType(
  debugName: String,
  lazyParser: LazyParser? = null,
): SyntaxElementType =
  SyntaxElementType(debugName, lazyParser, null as Any?)

private val counter = AtomicInt(0)
