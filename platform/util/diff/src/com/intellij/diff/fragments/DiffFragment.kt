// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.fragments

/**
 * Modified part of the text
 */
interface DiffFragment {
  val startOffset1: Int

  val endOffset1: Int

  val startOffset2: Int

  val endOffset2: Int
}
