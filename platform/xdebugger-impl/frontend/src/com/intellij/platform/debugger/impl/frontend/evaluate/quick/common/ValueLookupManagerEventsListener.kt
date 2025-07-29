// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.evaluate.quick.common

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.rpc.LOOKUP_HINTS_EVENTS_REMOTE_TOPIC
import com.intellij.platform.debugger.impl.rpc.ValueHintEvent
import com.intellij.platform.rpc.topics.ProjectRemoteTopicListener

private class ValueLookupManagerEventsListener : ProjectRemoteTopicListener<ValueHintEvent> {
  override val topic = LOOKUP_HINTS_EVENTS_REMOTE_TOPIC

  override fun handleEvent(project: Project, event: ValueHintEvent) {
    when (event) {
      is ValueHintEvent.HideHint -> {
        runInEdt {
          ValueLookupManager.getInstance(project).hideHint()
        }
      }
      is ValueHintEvent.StartListening -> {
        runInEdt {
          ValueLookupManager.getInstance(project).startListening()
        }
      }
    }
  }
}