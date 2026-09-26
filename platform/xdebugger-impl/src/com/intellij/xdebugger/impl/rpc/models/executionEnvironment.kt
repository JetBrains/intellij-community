// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc.models

import com.intellij.execution.ExecutionEnvironmentIdImpl
import com.intellij.execution.findExecutionEnvironment
import com.intellij.execution.runners.ExecutionEnvironment

internal fun ExecutionEnvironmentIdImpl.findValue(): ExecutionEnvironment? {
  return this.findExecutionEnvironment()
}
