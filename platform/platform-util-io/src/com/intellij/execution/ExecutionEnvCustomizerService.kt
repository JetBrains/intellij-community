// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution

import com.intellij.execution.configurations.GeneralCommandLine
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ExecutionEnvCustomizerService {
  fun customizeEnv(commandLine: GeneralCommandLine, environment: MutableMap<String, String>)
}