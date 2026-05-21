// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.ai.review.model

import com.intellij.openapi.Disposable
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Thread-safe storage for AI review problems.
 *
 * This is NOT a project-level service - it is created per [AIReviewSession].
 */
@ApiStatus.Internal
class AIReviewProblemsHolder : Disposable {

  private val lock = ReentrantReadWriteLock()
  private val problemsByPath = HashMap<String, MutableList<AIReviewResult.Problem>>()

  fun addProblems(newProblems: Collection<AIReviewResult.Problem>) {
    if (newProblems.isEmpty()) return

    lock.write {
      for (problem in newProblems) {
        problemsByPath.getOrPut(problem.path) { mutableListOf() }.add(problem)
      }
    }
  }

  fun clear() {
    lock.write {
      problemsByPath.clear()
    }
  }

  fun getCollectedProblems(): Map<String, List<AIReviewResult.Problem>> {
    return lock.read { problemsByPath.toMap() }
  }

  override fun dispose() {
    clear()
  }
}
