// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.fragments

import org.jetbrains.annotations.ApiStatus
import kotlin.jvm.JvmName

/**
 * Modified part of the text
 *
 * Offset ranges cover whole line, including '\n' at the end. But '\n' can be absent for the last line.
 */
interface LineFragment : DiffFragment {
  val startLine1: Int

  val endLine1: Int

  val startLine2: Int

  val endLine2: Int

  /**
   * High-granularity changes inside line fragment (ex: detected by ByWord)
   * Offsets of inner changes are relative to the start of LineFragment.
   *
   * null - no inner similarities was found
   */
  val innerFragments: List<DiffFragment>?


  // for preservation of source compatibility with Kotlin code after j2k

  @Suppress("INAPPLICABLE_JVM_NAME")
  @JvmName("getStartLine1DoNotUse")
  @ApiStatus.Internal
  @Deprecated("Use #startLine1 instead", ReplaceWith("startLine1"))
  fun getStartLine1(): Int = startLine1

  @Suppress("INAPPLICABLE_JVM_NAME")
  @JvmName("getEndLine1DoNotUse")
  @ApiStatus.Internal
  @Deprecated("Use #endLine1 instead", ReplaceWith("endLine1"))
  fun getEndLine1(): Int = endLine1

  @Suppress("INAPPLICABLE_JVM_NAME")
  @JvmName("getStartLine2DoNotUse")
  @ApiStatus.Internal
  @Deprecated("Use #startLine2 instead", ReplaceWith("startLine2"))
  fun getStartLine2(): Int = startLine2

  @Suppress("INAPPLICABLE_JVM_NAME")
  @JvmName("getEndLine2DoNotUse")
  @ApiStatus.Internal
  @Deprecated("Use #endLine2 instead", ReplaceWith("endLine2"))
  fun getEndLine2(): Int = endLine2

  @Suppress("INAPPLICABLE_JVM_NAME")
  @JvmName("getInnerFragmentsDoNotUse")
  @ApiStatus.Internal
  @Deprecated("Use #innerFragments instead", ReplaceWith("innerFragments"))
  fun getInnerFragments(): List<DiffFragment>? = innerFragments
}
