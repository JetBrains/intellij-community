// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.shared

import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.rpc.XDebugSessionId
import com.intellij.xdebugger.frame.XDescriptor
import org.jetbrains.annotations.ApiStatus

/**
 * Provides access to custom frontend descriptor states created via [CustomDescriptorStateManagerExtension].
 *
 * This interface is located in the shared module for migration reasons.
 * In the future it will be moved to the frontend module.
 */
@ApiStatus.Internal
interface CustomDescriptorStateManager {
  fun getState(descriptor: XDescriptor): Any?

  fun getProcessDescriptorState(sessionId: XDebugSessionId): Any?

  companion object {
    @JvmStatic
    fun getInstance(project: Project): CustomDescriptorStateManager =
      project.serviceOrNull() ?: error("CustomDescriptorStateManager should not be accessed from backend")
  }
}
