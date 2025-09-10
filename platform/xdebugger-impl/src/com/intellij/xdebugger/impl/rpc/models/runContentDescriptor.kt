// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc.models

import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.platform.kernel.ids.BackendValueIdType
import com.intellij.platform.kernel.ids.findValueById
import com.intellij.platform.kernel.ids.storeValueGlobally
import com.intellij.xdebugger.impl.rpc.RunContentDescriptorId
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

internal fun RunContentDescriptorId.findValue(): RunContentDescriptor? {
  return findValueById(this, type = RunContentDescriptorValueIdType)
}

internal fun RunContentDescriptor.storeGlobally(coroutineScope: CoroutineScope): RunContentDescriptorId {
  return storeValueGlobally(coroutineScope, this, type = RunContentDescriptorValueIdType)
}

private object RunContentDescriptorValueIdType : BackendValueIdType<RunContentDescriptorId, RunContentDescriptor>(::RunContentDescriptorId)
