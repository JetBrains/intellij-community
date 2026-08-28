// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemsView.backend

import com.intellij.analysis.problemsView.Problem
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
internal class ProjectErrorsIdStore {
  private val idsByProblem = ConcurrentHashMap<Problem, String>()

  fun getOrCreate(problem: Problem): String {
    return idsByProblem.computeIfAbsent(problem) { UUID.randomUUID().toString() }
  }

  fun remove(problem: Problem): String? = idsByProblem.remove(problem)

  companion object {
    fun getInstance(project: Project): ProjectErrorsIdStore = project.service()
  }
}
