// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import org.assertj.core.api.Assertions.assertThat
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.configuration.ProjectConfigurationFinishEvent
import org.gradle.tooling.events.configuration.ProjectConfigurationOperationDescriptor
import org.gradle.tooling.events.configuration.ProjectConfigurationStartEvent
import org.gradle.tooling.events.lifecycle.BuildPhaseFinishEvent
import org.gradle.tooling.events.lifecycle.BuildPhaseOperationDescriptor
import org.gradle.tooling.events.lifecycle.BuildPhaseStartEvent
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskOperationDescriptor
import org.gradle.tooling.events.task.TaskStartEvent
import org.gradle.tooling.model.ProjectIdentifier
import org.jetbrains.plugins.gradle.service.execution.GradleProgressPhase.*
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import java.util.stream.Stream

internal class GradleProgressIndicatorEventHelperTest {

  @Test
  fun `test Gradle build phase is updated on events`() {
    // We start with new state
    var state = GradleProgressState.newInitializationState()

    // Configuration phase
    var event = buildPhaseStartEvent("CONFIGURE_ROOT_BUILD", 1)
    state = GradleProgressIndicatorEventHelper.updateGradleProgressState(state, event)
    assertThat(state).isEqualTo(GradleProgressState(CONFIGURATION, 1))

    event = buildPhaseStartEvent("CONFIGURE_BUILD", 1)
    state = GradleProgressIndicatorEventHelper.updateGradleProgressState(state, event)
    assertThat(state).isEqualTo(GradleProgressState(CONFIGURATION, 2))

    event = buildPhaseFinishEvent("CONFIGURE_BUILD")
    state = GradleProgressIndicatorEventHelper.updateGradleProgressState(state, event)
    assertThat(state).isEqualTo(GradleProgressState(CONFIGURATION, 2))

    event = buildPhaseFinishEvent("CONFIGURE_BUILD")
    state = GradleProgressIndicatorEventHelper.updateGradleProgressState(state, event)
    assertThat(state).isEqualTo(GradleProgressState(CONFIGURATION, 2))

    event = buildPhaseFinishEvent("CONFIGURE_ROOT_BUILD")
    state = GradleProgressIndicatorEventHelper.updateGradleProgressState(state, event)
    assertThat(state).isEqualTo(GradleProgressState(CONFIGURATION_DONE, 2))

    // Execution phase
    event = buildPhaseStartEvent("RUN_MAIN_TASKS", 1)
    state = GradleProgressIndicatorEventHelper.updateGradleProgressState(state, event)
    assertThat(state).isEqualTo(GradleProgressState(EXECUTION, 1))

    event = buildPhaseStartEvent("RUN_WORK", 1)
    state = GradleProgressIndicatorEventHelper.updateGradleProgressState(state, event)
    assertThat(state).isEqualTo(GradleProgressState(EXECUTION, 2))

    event = buildPhaseFinishEvent("RUN_WORK")
    state = GradleProgressIndicatorEventHelper.updateGradleProgressState(state, event)
    assertThat(state).isEqualTo(GradleProgressState(EXECUTION, 2))

    event = buildPhaseFinishEvent("RUN_MAIN_TASKS")
    state = GradleProgressIndicatorEventHelper.updateGradleProgressState(state, event)
    assertThat(state).isEqualTo(GradleProgressState(EXECUTION_DONE, 2))

    // Back to configuration phase (case when continuous build)
    event = buildPhaseStartEvent("CONFIGURE_ROOT_BUILD", 1)
    state = GradleProgressIndicatorEventHelper.updateGradleProgressState(state, event)
    assertThat(state).isEqualTo(GradleProgressState(CONFIGURATION, 1))
  }

  @Test
  fun `test Gradle current phase is not updated on RUN_MAIN_TASKS and RUN_WORK events if in configuration phase`() {
    var state = GradleProgressState(CONFIGURATION, 1)

    // Nothing is updated
    var event = buildPhaseStartEvent("RUN_MAIN_TASKS", 1)
    state = GradleProgressIndicatorEventHelper.updateGradleProgressState(state, event)
    assertThat(state).isEqualTo(GradleProgressState(CONFIGURATION, 1))

    // Nothing is updated
    event = buildPhaseStartEvent("RUN_WORK", 1)
    state = GradleProgressIndicatorEventHelper.updateGradleProgressState(state, event)
    assertThat(state).isEqualTo(GradleProgressState(CONFIGURATION, 1))
  }

  @Test
  fun `test Gradle current phase is not updated on CONFIGURE_BUILD phase event if in execution phase`() {
    var state = GradleProgressState(EXECUTION, 1)

    // Nothing is updated
    val event = buildPhaseStartEvent("CONFIGURE_BUILD", 2)
    state = GradleProgressIndicatorEventHelper.updateGradleProgressState(state, event)
    assertThat(state).isEqualTo(GradleProgressState(EXECUTION, 1))
  }

  @Test
  fun `test Gradle progress state is never updated in Initialization phase`() {
    var state = GradleProgressState.newInitializationState()

    // Nothing changes on project configuration events
    var event = projectConfigurationStartEvent(":my-project")
    state = GradleProgressIndicatorEventHelper.updateGradleProgressState(state, event)
    assertThat(state).isEqualTo(GradleProgressState.newInitializationState())

    event = projectConfigurationFinishEvent(":my-project")
    state = GradleProgressIndicatorEventHelper.updateGradleProgressState(state, event)
    assertThat(state).isEqualTo(GradleProgressState.newInitializationState())

    // Nothing changes on task events
    event = taskStartEvent(":my-project:test")
    state = GradleProgressIndicatorEventHelper.updateGradleProgressState(state, event)
    assertThat(state).isEqualTo(GradleProgressState.newInitializationState())

    event = taskFinishEvent(":my-project:test")
    state = GradleProgressIndicatorEventHelper.updateGradleProgressState(state, event)
    assertThat(state).isEqualTo(GradleProgressState.newInitializationState())
  }

  @Test
  fun `test Gradle progress state is updated when in configuration phase on ProjectConfigurationProgressEvent Events`() {
    var state = GradleProgressState(CONFIGURATION, 10)

    // On project configuration start current progress is incremented and project is added to runningWorkItems
    var event = projectConfigurationStartEvent(":my-project")
    state = GradleProgressIndicatorEventHelper.updateGradleProgressState(state, event)
    assertThat(state).isEqualTo(
      GradleProgressState(CONFIGURATION, 10, currentProgress = 1, runningWorkItems = linkedSetOf(":my-project"))
    )

    // On project configuration finish project is removed from runningWorkItems
    event = projectConfigurationFinishEvent(":my-project")
    state = GradleProgressIndicatorEventHelper.updateGradleProgressState(state, event)
    assertThat(state).isEqualTo(
      GradleProgressState(CONFIGURATION, 10, currentProgress = 1, runningWorkItems = linkedSetOf())
    )

    // Nothing changes on task events
    event = taskStartEvent(":my-project:test")
    state = GradleProgressIndicatorEventHelper.updateGradleProgressState(state, event)
    assertThat(state).isEqualTo(
      GradleProgressState(CONFIGURATION, 10, currentProgress = 1, runningWorkItems = linkedSetOf())
    )

    event = taskFinishEvent(":my-project:test")
    state = GradleProgressIndicatorEventHelper.updateGradleProgressState(state, event)
    assertThat(state).isEqualTo(
      GradleProgressState(CONFIGURATION, 10, currentProgress = 1, runningWorkItems = linkedSetOf())
    )
  }

  @Test
  fun `test Gradle progress state is updated in execution phase on TaskProgressEvent Events`() {
    var state = GradleProgressState(EXECUTION, 10)

    // Nothing is changed on project configuration events
    var event = projectConfigurationStartEvent(":my-project")
    state = GradleProgressIndicatorEventHelper.updateGradleProgressState(state, event)
    assertThat(state).isEqualTo(GradleProgressState(EXECUTION, 10))

    event = projectConfigurationFinishEvent(":my-project")
    state = GradleProgressIndicatorEventHelper.updateGradleProgressState(state, event)
    assertThat(state).isEqualTo(GradleProgressState(EXECUTION, 10))

    // On task start events  current progress is incremented and task is added to runningWorkItems
    event = taskStartEvent(":my-project:test")
    state = GradleProgressIndicatorEventHelper.updateGradleProgressState(state, event)
    assertThat(state).isEqualTo(
      GradleProgressState(EXECUTION, 10, currentProgress = 1, runningWorkItems = linkedSetOf(":my-project:test"))
    )

    // On task finish events task is removed from runningWorkItems
    event = taskFinishEvent(":my-project:test")
    state = GradleProgressIndicatorEventHelper.updateGradleProgressState(state, event)
    assertThat(state).isEqualTo(
      GradleProgressState(EXECUTION, 10, currentProgress = 1, runningWorkItems = linkedSetOf())
    )
  }

  @ParameterizedTest
  @MethodSource("progressIndicatorValidEventsData")
  fun `test progress indicator events are generated for Project and Task Events in correct phases`(phase: GradleProgressPhase, event: ProgressEvent) {
    val state = GradleProgressState(phase, 10, runningWorkItems = linkedSetOf("some item"))
    val taskId = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, "")

    val progressIndicatorEvent = GradleProgressIndicatorEventHelper.createProgressIndicatorEvent(taskId, "", event, state)
    assertThat(progressIndicatorEvent).isNotNull
  }

  @ParameterizedTest
  @MethodSource("progressIndicatorInvalidEventsData")
  fun `test progress indicator events are not generated for events in incorrect phases`(phase: GradleProgressPhase, event: ProgressEvent) {
    val state = GradleProgressState(phase, 10, runningWorkItems = linkedSetOf("some item"))
    val taskId = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, "")

    val progressIndicatorEvent = GradleProgressIndicatorEventHelper.createProgressIndicatorEvent(taskId, "", event, state)
    assertThat(progressIndicatorEvent).isNull()
  }


  companion object {
    @JvmStatic
    fun progressIndicatorValidEventsData(): Stream<Arguments> {
      return Stream.of(
        Arguments.of(EXECUTION, taskStartEvent("task")),
        Arguments.of(EXECUTION, taskFinishEvent("task")),
        Arguments.of(EXECUTION_DONE, buildPhaseFinishEvent("RUN_MAIN_TASKS")),
        Arguments.of(CONFIGURATION, projectConfigurationStartEvent("project")),
        Arguments.of(CONFIGURATION, projectConfigurationFinishEvent("project")),
      )
    }

    @JvmStatic
    fun progressIndicatorInvalidEventsData(): Stream<Arguments> {
      return Stream.of(
        Arguments.of(INITIALIZATION, taskStartEvent("task")),
        Arguments.of(INITIALIZATION, taskFinishEvent("task")),
        Arguments.of(CONFIGURATION, taskStartEvent("task")),
        Arguments.of(CONFIGURATION, taskFinishEvent("task")),

        Arguments.of(INITIALIZATION, projectConfigurationStartEvent("project")),
        Arguments.of(INITIALIZATION, projectConfigurationFinishEvent("project")),
        Arguments.of(EXECUTION, projectConfigurationStartEvent("project")),
        Arguments.of(EXECUTION, projectConfigurationFinishEvent("project")),

        Arguments.of(INITIALIZATION, buildPhaseStartEvent("SOME_PHASE", 1)),
        Arguments.of(EXECUTION, buildPhaseStartEvent("SOME_PHASE", 1)),
        Arguments.of(CONFIGURATION, buildPhaseStartEvent("SOME_PHASE", 1)),
        Arguments.of(EXECUTION_DONE, buildPhaseFinishEvent("SOME_PHASE")),
      )
    }

    private fun buildPhaseStartEvent(buildPhase: String, totalWorkItems: Int): ProgressEvent {
      val event = mock(BuildPhaseStartEvent::class.java)
      val descriptor = mock(BuildPhaseOperationDescriptor::class.java)
      given(descriptor.buildPhase).willReturn(buildPhase)
      given(descriptor.buildItemsCount).willReturn(totalWorkItems)
      given(event.descriptor).willReturn(descriptor)
      return event
    }

    private fun buildPhaseFinishEvent(buildPhase: String): ProgressEvent {
      val event = mock(BuildPhaseFinishEvent::class.java)
      val descriptor = mock(BuildPhaseOperationDescriptor::class.java)
      given(descriptor.buildPhase).willReturn(buildPhase)
      given(event.descriptor).willReturn(descriptor)
      return event
    }

    private fun taskStartEvent(taskPath: String): ProgressEvent {
      val event = mock(TaskStartEvent::class.java)
      val descriptor = mock(TaskOperationDescriptor::class.java)
      given(descriptor.taskPath).willReturn(taskPath)
      given(event.descriptor).willReturn(descriptor)
      return event
    }

    private fun taskFinishEvent(taskPath: String): ProgressEvent {
      val event = mock(TaskFinishEvent::class.java)
      val descriptor = mock(TaskOperationDescriptor::class.java)
      given(descriptor.taskPath).willReturn(taskPath)
      given(event.descriptor).willReturn(descriptor)
      return event
    }

    private fun projectConfigurationStartEvent(projectPath: String): ProgressEvent {
      val event = mock(ProjectConfigurationStartEvent::class.java)
      val descriptor = mock(ProjectConfigurationOperationDescriptor::class.java)
      val projectIdentifier = mock(ProjectIdentifier::class.java)
      given(projectIdentifier.projectPath).willReturn(projectPath)
      given(descriptor.project).willReturn(projectIdentifier)
      given(event.descriptor).willReturn(descriptor)
      return event
    }

    private fun projectConfigurationFinishEvent(projectPath: String): ProgressEvent {
      val event = mock(ProjectConfigurationFinishEvent::class.java)
      val descriptor = mock(ProjectConfigurationOperationDescriptor::class.java)
      val projectIdentifier = mock(ProjectIdentifier::class.java)
      given(projectIdentifier.projectPath).willReturn(projectPath)
      given(descriptor.project).willReturn(projectIdentifier)
      given(event.descriptor).willReturn(descriptor)
      return event
    }
  }
}