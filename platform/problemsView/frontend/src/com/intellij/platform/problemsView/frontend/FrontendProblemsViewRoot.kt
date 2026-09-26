// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemsView.frontend

import com.intellij.analysis.problemsView.toolWindow.splitApi.ProblemDto
import com.intellij.analysis.problemsView.toolWindow.splitApi.ProblemEventDto

internal interface FrontendProblemsViewRoot {

  fun handleProblemEvents(problemEventDtos: List<ProblemEventDto>) {
    for (event in problemEventDtos) {
      when (event) {
        is ProblemEventDto.Appeared -> handleProblemAppeared(event.problemDto)
        is ProblemEventDto.Disappeared -> handleProblemDisappeared(event.problemId)
        is ProblemEventDto.Updated -> handleProblemUpdated(event.problemDto)
      }
    }
  }

  fun handleProblemAppeared(problemDto: ProblemDto)
  fun handleProblemDisappeared(problemId: String)
  fun handleProblemUpdated(problemDto: ProblemDto)
}