package com.intellij.remoteServer.ir.target

import com.intellij.execution.ExecutionTarget
import com.intellij.remoteServer.ir.IR

abstract class IRExecutionTarget : ExecutionTarget() {
  abstract fun getRemoteRunner(): IR.RemoteRunner
}