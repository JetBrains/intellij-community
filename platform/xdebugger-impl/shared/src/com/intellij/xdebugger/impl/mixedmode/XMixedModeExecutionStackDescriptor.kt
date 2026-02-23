// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.mixedmode

import com.intellij.platform.debugger.impl.rpc.XSourcePositionDto
import com.intellij.xdebugger.frame.CustomXDescriptorSerializerProvider
import com.intellij.xdebugger.frame.XDescriptor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

const val MIXED_MODE_EXECUTION_STACK_DESCRIPTOR_ID: String = "XMixedModeExecutionStack"

@ApiStatus.Internal
@Serializable
class XMixedModeExecutionStackDescriptor(
  val highStackDescriptor: XDescriptor?,
  val lowStackDescriptor: XDescriptor?,
  val topFramePosition: CompletableDeferred<XSourcePositionDto?>,
) : XDescriptor {

  override val kind: String = MIXED_MODE_EXECUTION_STACK_DESCRIPTOR_ID

}

internal class XMixedModeExecutionStackDescriptorSerializerProvider : CustomXDescriptorSerializerProvider {
  override fun getSerializer(kind: String): KSerializer<out XDescriptor>? {
    if (kind == MIXED_MODE_EXECUTION_STACK_DESCRIPTOR_ID) {
      return XMixedModeExecutionStackDescriptor.serializer()
    }
    return null
  }
}