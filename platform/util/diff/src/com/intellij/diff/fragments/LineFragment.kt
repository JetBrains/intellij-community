// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.fragments

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
}
