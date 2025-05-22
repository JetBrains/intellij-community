// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.fragments

import org.jetbrains.annotations.ApiStatus
import kotlin.jvm.JvmName

/**
 * Modified part of the text
 */
interface DiffFragment {
  val startOffset1: Int

  val endOffset1: Int

  val startOffset2: Int

  val endOffset2: Int

  // for preservation of source compatibility with Kotlin code after j2k

  @Suppress("INAPPLICABLE_JVM_NAME")
  @JvmName("getStartOffset1DoNotUse")
  @ApiStatus.Internal
  @Deprecated("Use #startOffset1 instead", ReplaceWith("startOffset1"))
  fun getStartOffset1(): Int = startOffset1

  @Suppress("INAPPLICABLE_JVM_NAME")
  @JvmName("getEndOffset1DoNotUse")
  @ApiStatus.Internal
  @Deprecated("Use #endOffset1 instead", ReplaceWith("endOffset1"))
  fun getEndOffset1(): Int = endOffset1

  @Suppress("INAPPLICABLE_JVM_NAME")
  @JvmName("getStartOffset2DoNotUse")
  @ApiStatus.Internal
  @Deprecated("Use #startOffset2 instead", ReplaceWith("startOffset2"))
  fun getStartOffset2(): Int = startOffset2

  @Suppress("INAPPLICABLE_JVM_NAME")
  @JvmName("getEndOffset2DoNotUse")
  @ApiStatus.Internal
  @Deprecated("Use #endOffset2 instead", ReplaceWith("endOffset2"))
  fun getEndOffset2(): Int = endOffset2
}
