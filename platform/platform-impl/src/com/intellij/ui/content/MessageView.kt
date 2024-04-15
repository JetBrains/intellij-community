// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.content

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval

interface MessageView {
  val contentManager: ContentManager

  fun runWhenInitialized(runnable: Runnable)

  @Deprecated("use {@link MessageView#getInstance(Project)} instead")
  @ScheduledForRemoval
  object SERVICE {
    @JvmStatic
    fun getInstance(project: Project): MessageView {
      return Companion.getInstance(project)
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): MessageView {
      return project.service<MessageView>()
    }
  }
}
