// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.rpc.XDebugSessionId
import com.intellij.platform.debugger.impl.shared.CustomDescriptorStateManager
import com.intellij.platform.debugger.impl.shared.CustomDescriptorStateManagerExtension
import com.intellij.xdebugger.frame.XDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import java.util.concurrent.ConcurrentHashMap

internal class FrontendCustomDescriptorStateManager : CustomDescriptorStateManager {
  private val states = ConcurrentHashMap<XDescriptor, Any>()
  private val processDescriptors = ConcurrentHashMap<XDebugSessionId, XDescriptor>()

  fun registerDescriptor(descriptor: XDescriptor, cs: CoroutineScope) {
    val state = CustomDescriptorStateManagerExtension.createState(descriptor, cs) ?: return
    states[descriptor] = state
    cs.coroutineContext.job.invokeOnCompletion { states.remove(descriptor) }
  }

  fun registerProcessDescriptor(sessionId: XDebugSessionId, descriptor: XDescriptor, cs: CoroutineScope) {
    registerDescriptor(descriptor, cs)
    processDescriptors[sessionId] = descriptor
    cs.coroutineContext.job.invokeOnCompletion { processDescriptors.remove(sessionId) }
  }

  override fun getState(descriptor: XDescriptor): Any? {
    return states[descriptor]
  }

  override fun getProcessDescriptorState(sessionId: XDebugSessionId): Any? {
    val descriptor = processDescriptors[sessionId] ?: return null
    return states[descriptor]
  }

  companion object {
    fun getInstance(project: Project): FrontendCustomDescriptorStateManager {
      return CustomDescriptorStateManager.getInstance(project) as FrontendCustomDescriptorStateManager
    }
  }
}
