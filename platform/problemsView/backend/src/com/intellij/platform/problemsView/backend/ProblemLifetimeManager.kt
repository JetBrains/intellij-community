// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemsView.backend

import com.intellij.analysis.problemsView.Problem
import com.intellij.analysis.problemsView.toolWindow.splitApi.ProblemLifetime
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
internal class ProblemLifetimeManager {

  private val problemIds = IdValueStore<Problem>()

  fun getOrCreateProblemId(problem: Problem, lifetime: ProblemLifetime): String {
    val id = problemIds.getOrCreateId(problem)
    lifetime.bindIdToLifetime(id)

    return id
  }

  fun removeProblemId(problem: Problem, lifetime: ProblemLifetime): String? {
    val problemId = problemIds.remove(problem) ?: return null

    lifetime.unbindId(problemId)
    return problemId
  }

  private fun ProblemLifetime.bindIdToLifetime(id: String): Boolean {
    ensureCompletionHandlerRegistered {
      unbindAllIds().forEach(::removeStoredId)
    }

    if (bindId(id)) return true

    // the lifetime completed before id could be bound, so we remove it explicitly from the IdValueStore
    removeStoredId(id)
    return false
  }

  private fun removeStoredId(id: String) {
    problemIds.removeById(id)
  }

  companion object {
    fun getInstance(project: Project): ProblemLifetimeManager = project.service()
  }
}
