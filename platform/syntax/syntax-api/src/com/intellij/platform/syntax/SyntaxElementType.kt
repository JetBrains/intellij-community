// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax

import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicInteger

/**
 * A class defining a token or node type.
 *
 * [debugName] is used only for debug purposes.
 */
@ApiStatus.Experimental
class SyntaxElementType(
  private val debugName: String,
) {
  val index: Int = counter.getAndIncrement()

  override fun toString(): String = debugName

  override fun equals(other: Any?): Boolean = this === other

  override fun hashCode(): Int = index
}

private val counter = AtomicInteger(0)