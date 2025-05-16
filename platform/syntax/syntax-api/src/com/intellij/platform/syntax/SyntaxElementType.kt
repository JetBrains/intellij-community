// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalAtomicApi::class)
@file:ApiStatus.Experimental

package com.intellij.platform.syntax

import org.jetbrains.annotations.ApiStatus
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndIncrement

/**
 * A class defining a token or node type.
 *
 * [debugName] is used only for debug purposes.
 */
@ApiStatus.Experimental
class SyntaxElementType internal constructor(
  private val debugName: String,
  @Suppress("unused") unusedParam: Any?, // this parameter is necessary for disambiguation with the factory function
) {
  val index: Int = counter.fetchAndIncrement()

  override fun toString(): String = debugName

  override fun equals(other: Any?): Boolean = this === other

  override fun hashCode(): Int = index
}

@ApiStatus.Experimental
fun SyntaxElementType(
  debugName: String,
): SyntaxElementType =
  SyntaxElementType(debugName, null as Any?)

private val counter = AtomicInt(0)
