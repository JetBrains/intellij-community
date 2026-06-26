// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.engine.platform

import com.intellij.agent.workbench.engine.core.ThreadEventEnvelope
import com.intellij.agent.workbench.engine.core.ThreadId
import com.intellij.util.messages.Topic

/**
 * Project-level publish/subscribe contract for Engine. UI components subscribe to react to new
 * events and projection refreshes without polling the store.
 */
interface EngineEvents {
  /** A new event was appended to the store. */
  fun eventAppended(event: ThreadEventEnvelope)

  /** The projection for [threadId] was (re)computed and is ready to be re-rendered. */
  fun projectionUpdated(threadId: ThreadId)

  companion object {
    @JvmField
    @Topic.ProjectLevel
    val TOPIC: Topic<EngineEvents> = Topic.create("Engine Events", EngineEvents::class.java)
  }
}
