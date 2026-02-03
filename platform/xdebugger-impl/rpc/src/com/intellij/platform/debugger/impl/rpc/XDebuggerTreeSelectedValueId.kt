// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.rpc

import com.intellij.xdebugger.frame.XValueNode
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.ApiStatus

/**
 * Used to transfer the selection in the frontend tree to the backend.
 */
@ApiStatus.Internal
@Serializable
data class XDebuggerTreeSelectedValueId(
  val id: XValueId?,
  val name: String?,
  @Transient val node: XValueNode? = null,
)