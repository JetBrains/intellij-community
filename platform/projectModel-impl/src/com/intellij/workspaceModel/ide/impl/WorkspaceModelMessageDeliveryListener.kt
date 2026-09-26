// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.platform.backend.workspace.WorkspaceModelTopics
import com.intellij.util.messages.Topic
import com.intellij.util.messages.impl.MessageDeliveryListener
import java.util.concurrent.TimeUnit

internal object WorkspaceModelMessageDeliveryListener: MessageDeliveryListener {
  override fun messageDelivered(topic: Topic<*>, messageName: String, handler: Any, durationNanos: Long) {
    if (topic == WorkspaceModelTopics.CHANGED) {
      if (TimeUnit.NANOSECONDS.toMillis(durationNanos) > 200) {
        thisLogger().warn(String.format("Long WSM event processing. Topic=%s, offender=%s, message=%s, time=%dms",
                                        topic.displayName, handler.javaClass, messageName, TimeUnit.NANOSECONDS.toMillis(durationNanos)));
      }
    }
  }
}