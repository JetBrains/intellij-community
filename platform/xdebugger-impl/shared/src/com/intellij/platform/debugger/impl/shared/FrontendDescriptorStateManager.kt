// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.shared

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.frame.XDescriptor
import fleet.multiplatform.shims.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import org.jetbrains.annotations.ApiStatus

/**
 * Creates a plugin-specific state for a given [XDescriptor].
 * For example, if a [XDescriptor] contains a flow, this extension should create an object which will keep the flow's state.
 */
@ApiStatus.Internal
interface FrontendDescriptorStateManagerExtension {
  fun createState(descriptor: XDescriptor, cs: CoroutineScope): Any?

  companion object {
    private val EP_NAME = ExtensionPointName.create<FrontendDescriptorStateManagerExtension>("com.intellij.xdebugger.descriptorStateManager")

    fun createState(descriptor: XDescriptor, cs: CoroutineScope): Any? {
      return EP_NAME.extensionList.firstNotNullOfOrNull { it.createState(descriptor, cs) }
    }
  }
}

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class FrontendDescriptorStateManager {
  private val states = ConcurrentHashMap<XDescriptor, Any>()

  fun registerDescriptor(descriptor: XDescriptor, cs: CoroutineScope) {
    val state = FrontendDescriptorStateManagerExtension.createState(descriptor, cs) ?: return
    states[descriptor] = state
    cs.coroutineContext.job.invokeOnCompletion { states.remove(descriptor) }
  }

  fun getState(descriptor: XDescriptor): Any? {
    return states[descriptor]
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): FrontendDescriptorStateManager = project.service()
  }
}
