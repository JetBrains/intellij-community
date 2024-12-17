// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.autotest

import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus

/**
 * Listener to be notified when auto run is enabled or disabled for tests.
 */
@ApiStatus.Internal
interface AutoTestListener {
  fun runConfigurationsChanged()

  companion object {
    @Topic.ProjectLevel
    val TOPIC: Topic<AutoTestListener> = Topic<AutoTestListener>(AutoTestListener::class.java, Topic.BroadcastDirection.TO_CHILDREN)
  }
}
