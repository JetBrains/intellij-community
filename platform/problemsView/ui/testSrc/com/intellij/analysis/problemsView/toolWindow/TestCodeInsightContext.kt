// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.codeInsight.multiverse.CodeInsightContext

/** A context with a name. Two contexts with the same name are equal. */
internal class TestCodeInsightContext(private val name: String) : CodeInsightContext {
  override fun toString(): String = name

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is TestCodeInsightContext) return false
    return name == other.name
  }

  override fun hashCode(): Int = name.hashCode()
}
