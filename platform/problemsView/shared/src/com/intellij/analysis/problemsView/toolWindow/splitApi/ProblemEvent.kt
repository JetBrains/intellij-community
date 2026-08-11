// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.problemsView.toolWindow.splitApi

import com.intellij.analysis.problemsView.Problem
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
sealed class ProblemEvent(val problem: Problem) {
  class Appeared(problem: Problem) : ProblemEvent(problem)
  class Disappeared(problem: Problem) : ProblemEvent(problem)
  class Updated(problem: Problem) : ProblemEvent(problem)
}
