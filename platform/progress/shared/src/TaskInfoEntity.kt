// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.progress

import com.intellij.platform.ide.progress.suspender.TaskSuspension
import com.intellij.platform.project.ProjectEntity
import com.intellij.platform.project.asProject
import com.intellij.platform.project.asProjectOrNull
import com.intellij.platform.util.progress.ProgressState
import com.jetbrains.rhizomedb.EID
import com.jetbrains.rhizomedb.Entity
import com.jetbrains.rhizomedb.RefFlags
import fleet.kernel.DurableEntityType
import kotlinx.serialization.builtins.serializer
import org.jetbrains.annotations.ApiStatus

/**
 * Represents the information of a task in the system.
 */
@ApiStatus.Internal
data class TaskInfoEntity(override val eid: EID) : Entity {
  /**
   * Project entity associated with a task.
   * To retrieve an instance of a project from the entity use [asProjectOrNull] or [asProject]
   *
   * The entity can be null for a default project
   */
  val projectEntity: ProjectEntity? by ProjectEntityType

  /**
   * Human-readable title of a task, which is used to display the task in UI
   */
  val title: String by TitleType

  /**
   * Specifies whether the task can be canceled.
   *
   * Possible values are:
   *
   * - [TaskCancellation.NonCancellable]: The task cannot be canceled, and no cancel button should be displayed in the UI.
   * - [TaskCancellation.Cancellable]: The task can be canceled, and an optional cancel button can be displayed with
   *   customizable button and tooltip text.
   */
  val cancellation: TaskCancellation by TaskCancellationType

  /**
   * Specifies whether the task can be suspended and resumed.
   *
   * Possible values are:
   *
   * - [TaskSuspension.NonSuspendable]: The task cannot be suspended, and no pause button should be displayed in the UI.
   * - [TaskSuspension.Suspendable]: The task can be suspended, and a pause button should be displayed
   */
  val suspension: TaskSuspension by TaskSuspensionType

  /**
   * Represents the current progress state of the task.
   *
   * This state includes:
   * - [ProgressState.fraction]: The fraction of the task that has been completed, where `1.0` means 100% complete.
   * - [ProgressState.text]: The primary text associated with the progress state (e.g., a brief description or title).
   * - [ProgressState.details]: The detailed text associated with the progress state (e.g., additional information or steps).
   *
   * To subscribe on the progress updates use [updates]
   * Initially, the progress state may be `null`.
   */
  val progressState: ProgressState? by ProgressStateType

  /**
   * Indicates the current status of the task.
   *
   * The task can have one of the following statuses defined by [TaskStatus]:
   * - [TaskStatus.Running]: The task is currently in progress.
   * - [TaskStatus.Paused]: The task has been temporarily paused.
   * - [TaskStatus.Canceled]: The task has been requested to stop, though it might still be running until it can be safely aborted.
   *
   * The status can be changed based on certain conditions:
   * - A running task can be suspended or canceled.
   * - A suspended task can be resumed or canceled.
   * - A canceled task does not change its status anymore.
   *
   * The status is managed by user through [TaskManager]
   * The status updates can be subscribed using [statuses]
   */
  var taskStatus: TaskStatus by TaskStatusType

  companion object : DurableEntityType<TaskInfoEntity>(
    TaskInfoEntity::class.java.name,
    "com.intellij.platform.ide.progress",
    ::TaskInfoEntity
  ) {
    val TitleType: Required<String> = requiredValue("title", String.serializer())
    val TaskCancellationType: Required<TaskCancellation> = requiredValue("taskCancellation", TaskCancellation.serializer())
    val TaskSuspensionType: Required<TaskSuspension> = requiredValue("isSuspendable", TaskSuspension.serializer())
    val ProgressStateType: Optional<ProgressState> = optionalValue("progressState", ProgressState.serializer())
    val TaskStatusType: Required<TaskStatus> = requiredValue("taskStatus", TaskStatus.serializer())
    val ProjectEntityType: Optional<ProjectEntity> = optionalRef<ProjectEntity>("project", RefFlags.CASCADE_DELETE_BY)
  }
}
