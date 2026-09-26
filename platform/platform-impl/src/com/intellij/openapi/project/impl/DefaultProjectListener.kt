// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.impl

import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface DefaultProjectListener {
  companion object {
    val TOPIC: Topic<DefaultProjectListener> = Topic(DefaultProjectListener::class.java)
  }

  @ApiStatus.Internal
  fun defaultProjectImplCreated(defaultProject: Project) {}
}