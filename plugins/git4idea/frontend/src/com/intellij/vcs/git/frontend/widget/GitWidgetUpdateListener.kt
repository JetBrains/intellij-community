// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.frontend.widget

import com.intellij.util.messages.Topic

internal interface GitWidgetUpdateListener {
  fun triggerUpdate()

  companion object {
    @JvmField
    @Topic.ProjectLevel
    val TOPIC: Topic<GitWidgetUpdateListener> = Topic(GitWidgetUpdateListener::class.java)
  }
}