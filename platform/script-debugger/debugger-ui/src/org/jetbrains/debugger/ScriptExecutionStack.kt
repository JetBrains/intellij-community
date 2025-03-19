// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.debugger

import com.intellij.openapi.util.NlsContexts
import com.intellij.xdebugger.frame.XExecutionStack
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class ScriptExecutionStack(val vm: Vm, @NlsContexts.ListItem displayName: String, icon: javax.swing.Icon)
  : XExecutionStack(displayName, icon) {

  override fun hashCode(): Int {
    return vm.hashCode()
  }

  override fun equals(other: Any?): Boolean {
    return other is ScriptExecutionStack && other.vm == vm
  }
}