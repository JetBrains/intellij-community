// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.actions

import com.intellij.openapi.application.readActionBlocking
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IncompleteDependenciesService
import com.intellij.openapi.project.IncompleteDependenciesService.DependenciesState
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

/**
 * Usage statistics has the relaxed guarantees for data consistency, so we just have some observation of dumb/dependencies state.
 * At the same time, it lets us avoid getting the read lock on every change in files.
 */
@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class ProjectStateObserver(private val project: Project, coroutineScope: CoroutineScope) {
  private val flow: MutableStateFlow<ProjectState> = MutableStateFlow(ProjectState(true, DependenciesState.COMPLETE))

  init {
    val connection = project.messageBus.connect(coroutineScope)
    connection.subscribe(DumbService.DUMB_MODE, object : DumbService.DumbModeListener {
      override fun enteredDumbMode() {
        updateState { state -> ProjectState(true, state.dependenciesState) }
      }

      override fun exitDumbMode() {
        updateState { state -> ProjectState(false, state.dependenciesState) }
      }
    })

    coroutineScope.launch {
      flow.value = ProjectState(
          DumbService.isDumb(project),
          readActionBlocking { project.service<IncompleteDependenciesService>().getState() }
      )

      project.service<IncompleteDependenciesService>().stateFlow.collect {
        updateState { state -> ProjectState(state.isDumb, it) }
      }
    }
  }

  fun getState(): ProjectState = flow.value

  private fun updateState(updater: (ProjectState) -> ProjectState) {
    while (true) {
      val state = flow.value
      val newState = updater(state)
      if (flow.compareAndSet(state, newState)) {
        return
      }
    }
  }
}

internal class ProjectStateObserverProjectActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    project.serviceAsync<ProjectStateObserver>()
  }
}

@ApiStatus.Internal
class ProjectState(
  val isDumb: Boolean,
  val dependenciesState: DependenciesState,
)