// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.evaluate.quick.common

import com.intellij.openapi.application.runInEdt
import com.intellij.platform.debugger.impl.rpc.LOOKUP_HINTS_EVENTS_REMOTE_TOPIC
import com.intellij.platform.debugger.impl.rpc.ValueHintEvent
import com.intellij.platform.project.findProject
import com.intellij.platform.rpc.topics.RemoteTopic
import com.intellij.platform.rpc.topics.RemoteTopicListener

private class ValueLookupManagerEventsListener : RemoteTopicListener<ValueHintEvent> {
  override val topic: RemoteTopic<ValueHintEvent> = LOOKUP_HINTS_EVENTS_REMOTE_TOPIC

  override fun handleEvent(event: ValueHintEvent) {
    when (event) {
      is ValueHintEvent.HideHint -> {
        runInEdt {
          ValueLookupManager.getInstance(event.project.findProject()).hideHint()
        }
      }
      is ValueHintEvent.StartListening -> {
        runInEdt {
          ValueLookupManager.getInstance(event.project.findProject()).startListening()
        }
      }
    }
  }
}