// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.vcs.impl.shared.commit.EditedCommitPresentation
import com.intellij.util.messages.Topic
import com.intellij.vcs.commit.ChangesViewCommitWorkflowHandler
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus
import java.util.EventListener

@ApiStatus.NonExtendable
abstract class ChangesViewWorkflowManager @ApiStatus.Internal protected constructor() {
  val commitWorkflowHandler: ChangesViewCommitWorkflowHandler?
    get() = doGetCommitWorkflowHandler()

  abstract val allowExcludeFromCommit: StateFlow<Boolean>
  abstract val editedCommit: StateFlow<EditedCommitPresentation?>

  @ApiStatus.Internal
  protected abstract fun doGetCommitWorkflowHandler(): ChangesViewCommitWorkflowHandler?

  @ApiStatus.Internal
  abstract fun setEditedCommit(editedCommit: EditedCommitPresentation?)

  fun interface ChangesViewWorkflowListener : EventListener {
    fun commitWorkflowChanged()
  }

  companion object {
    @JvmField
    @Topic.ProjectLevel
    val TOPIC: Topic<ChangesViewWorkflowListener> = Topic(ChangesViewWorkflowListener::class.java, Topic.BroadcastDirection.NONE, true)

    @JvmStatic
    fun getInstance(project: Project): ChangesViewWorkflowManager = project.service<ChangesViewWorkflowManager>()
  }
}
