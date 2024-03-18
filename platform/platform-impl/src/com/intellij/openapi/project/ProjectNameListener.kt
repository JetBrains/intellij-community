// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.util.messages.Topic
import java.util.*

/**
 * Represents a listener for name changes of Project
 */
interface ProjectNameListener: EventListener {
  fun nameChanged(newName: String?)

  companion object {
    @Topic.ProjectLevel
    @JvmField
    val TOPIC: Topic<ProjectNameListener> = Topic(ProjectNameListener::class.java, Topic.BroadcastDirection.NONE)
  }
}