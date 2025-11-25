// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import com.intellij.vcs.commit.ChangesViewCommitWorkflowHandler
import org.jetbrains.annotations.ApiStatus
import java.util.*

@ApiStatus.NonExtendable
abstract class ChangesViewWorkflowManager @ApiStatus.Internal protected constructor() {
  val commitWorkflowHandler: ChangesViewCommitWorkflowHandler?
    get() = doGetCommitWorkflowHandler()

  @ApiStatus.Internal
  protected abstract fun doGetCommitWorkflowHandler(): ChangesViewCommitWorkflowHandler?

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
