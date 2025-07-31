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
 * If the element types can be either permanent or transient. Permanent element types are assigned an index and can be stored in a set.
 * Transient element types are not assigned an index and cannot be stored in a set.
 *
 * Element types can be lazy-parseable. Nodes of such a type are parsed on demand. The lazy parser can also support incremental reparsing.
 *
 * [debugName] is used only for debug purposes.
 */
@ApiStatus.Experimental
class SyntaxElementType internal constructor(
  private val debugName: String,
  internal val lazyParser: LazyParser?,
  val userData: Any?,
  transient: Boolean,
  @Suppress("unused") unusedParam: Any?, // this parameter is necessary for disambiguation with the factory function
) {

  /**
   * The unique index of this element type
   *
   * If the element type is transient, the index will be -1.
   */
  val index: Int = if (transient) -1 else counter.fetchAndIncrement()

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

/**
 * Creates a new [SyntaxElementType].
 *
 * @param debugName the name of the element type for debug purposes.
 * @param lazyParser the lazy parser for this element type, or `null` if this element type is not lazy-parseable.
 * @param transient whether this element type is lightweight or not. If `true`, the element type will not be assigned an index and cannot be stored in a set.
 */
@ApiStatus.Experimental
@JvmOverloads
fun SyntaxElementType(
  debugName: String,
  lazyParser: LazyParser? = null,
  userData: Any? = null,
  transient: Boolean = false,
): SyntaxElementType =
  SyntaxElementType(debugName, lazyParser, userData, transient, null as Any?)

private val counter = AtomicInt(0)
