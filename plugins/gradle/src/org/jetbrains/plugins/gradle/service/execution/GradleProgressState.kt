// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution

import org.jetbrains.plugins.gradle.service.execution.GradleProgressPhase.CONFIGURATION

enum class GradleProgressPhase(private val order: Int) {
  INITIALIZATION(0),
  CONFIGURATION(1),
  EXECUTION(2);

  fun isAfter(other: GradleProgressPhase): Boolean {
    return order > other.order
  }
}

data class GradleProgressState(
  val currentPhase: GradleProgressPhase,
  val totalWorkItems: Long,
  val currentProgress: Long = 0,
  val isConfigurationDone: Boolean = currentPhase.isAfter(CONFIGURATION),
  val runningWorkItems: Set<String> = linkedSetOf(),
) {

  companion object {
    @JvmStatic
    fun newInitializationState() = GradleProgressState(GradleProgressPhase.INITIALIZATION, 0)
  }

  val firstRunningWorkItem: String?
    get() = if (runningWorkItems.isEmpty()) {
      null
    }
    else {
      runningWorkItems.iterator().next()
    }

  fun runningWorkItemsWithout(workItem: String): Set<String> {
    val workItems = linkedSetOf<String>()
    workItems.addAll(this.runningWorkItems)
    workItems.remove(workItem)
    return workItems
  }

  fun runningWorkItemsAnd(workItem: String): Set<String> {
    val workItems = linkedSetOf<String>()
    workItems.addAll(this.runningWorkItems)
    workItems.add(workItem)
    return workItems
  }

  fun applyIf(condition: Boolean, body: GradleProgressState.() -> GradleProgressState): GradleProgressState {
    return when {
      condition -> body.invoke(this)
      else -> this
    }
  }
}
