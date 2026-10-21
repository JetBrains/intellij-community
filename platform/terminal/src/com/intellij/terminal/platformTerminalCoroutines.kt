// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.cancelOnDispose
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job

@Service(Service.Level.APP)
private class PlatformTerminalApplicationCoroutineScopeProvider(val coroutineScope: CoroutineScope)

@Service(Service.Level.PROJECT)
private class PlatformTerminalProjectCoroutineScopeProvider(val coroutineScope: CoroutineScope)

internal fun platformTerminalApplicationScope(): CoroutineScope {
  return service<PlatformTerminalApplicationCoroutineScopeProvider>().coroutineScope
}

internal fun platformTerminalProjectScope(project: Project): CoroutineScope {
  return project.service<PlatformTerminalProjectCoroutineScopeProvider>().coroutineScope
}

/**
 * Returns a scope that lives as long as [widget].
 */
internal fun createScope(widget: JBTerminalWidget): CoroutineScope {
  val scope = platformTerminalProjectScope(widget.project).childScope("JBTerminalWidget")
  scope.coroutineContext.job.cancelOnDispose(widget)
  return scope
}
