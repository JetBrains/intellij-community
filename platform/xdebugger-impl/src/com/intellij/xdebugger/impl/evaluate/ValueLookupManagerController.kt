// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.evaluate

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.platform.debugger.impl.rpc.LOOKUP_HINTS_EVENTS_REMOTE_TOPIC
import com.intellij.platform.debugger.impl.rpc.ValueHintEvent
import com.intellij.platform.project.projectId
import com.intellij.platform.rpc.topics.broadcast
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class ValueLookupManagerController(private val project: Project, private val cs: CoroutineScope) {
  /**
   * Starts [ValueLookupManager] listening for events (e.g. mouse movement) to trigger evaluation popups
   */
  fun startListening() {
    LOOKUP_HINTS_EVENTS_REMOTE_TOPIC.broadcast(project, ValueHintEvent.StartListening)
  }

  /**
   * Requests [ValueLookupManager] to hide current evaluation hints
   */
  fun hideHint() {
    LOOKUP_HINTS_EVENTS_REMOTE_TOPIC.broadcast(project, ValueHintEvent.HideHint)
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): ValueLookupManagerController = project.service<ValueLookupManagerController>()

    /**
     * @see XDebuggerUtil#disableValueLookup(Editor)
     */
    @JvmField
    val DISABLE_VALUE_LOOKUP = Key.create<Boolean>("DISABLE_VALUE_LOOKUP");
  }
}