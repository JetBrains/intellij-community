// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.attach

import com.intellij.execution.process.ProcessInfo
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface XAttachMixedModeDebugger {
  fun attachMixedModeDebugSession(
    project: Project,
    attachHost: XAttachHost,
    processInfo: ProcessInfo,
  )
}