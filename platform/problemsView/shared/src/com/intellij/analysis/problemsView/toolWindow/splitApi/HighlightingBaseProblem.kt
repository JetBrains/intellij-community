// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.problemsView.toolWindow.splitApi

import com.intellij.analysis.problemsView.Problem
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface HighlightingBaseProblem : Problem {
  val severity: Int
  fun getQuickFixOffset(): Int = -1
}
