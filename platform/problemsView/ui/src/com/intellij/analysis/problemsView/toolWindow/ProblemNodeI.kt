// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.analysis.problemsView.Problem
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ProblemNodeI {
  val problem: Problem

  fun getText(): String

  fun getLine(): Int

  fun getColumn(): Int

  fun getSeverity(): Int
}
