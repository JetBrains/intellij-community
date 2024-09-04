// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.progress

import com.intellij.platform.util.progress.ProgressState
import com.jetbrains.rhizomedb.EID
import com.jetbrains.rhizomedb.Entity
import fleet.kernel.DurableEntityType
import kotlinx.serialization.builtins.serializer
import org.jetbrains.annotations.ApiStatus

/**
 * Represents the information of a task in the system.
 */
@ApiStatus.Internal
data class TaskInfoEntity(override val eid: EID) : Entity {
  /**
   * Human-readable title of a task, which is used to display the task in UI
   */
  val title: String by Title

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
   * - [TaskStatus.RUNNING]: The task is currently in progress.
   * - [TaskStatus.PAUSED]: The task has been temporarily paused.
   * - [TaskStatus.CANCELED]: The task has been requested to stop, though it might still be running until it can be safely aborted.
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
    var Title = requiredValue("title", String.serializer())
    var TaskCancellationType = requiredValue("taskCancellation", TaskCancellation.serializer())
    var ProgressStateType = optionalValue("progressState", ProgressState.serializer())
    var TaskStatusType = requiredValue("taskStatus", TaskStatus.serializer())
  }
}

