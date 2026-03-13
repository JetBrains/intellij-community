// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions.handlers

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.impl.actions.handlers.XDebuggerCustomMuteBreakpointHandler.Companion.EP_NAME
import org.jetbrains.annotations.ApiStatus

/**
 * This API is intended to be used for languages and frameworks where debugging happens
 * without [com.intellij.xdebugger.XDebugSession] being initialized.
 *
 * So, this EP provides a way to customize Mute Breakpoints action without having current [com.intellij.xdebugger.XDebugSession].
 */
@ApiStatus.Internal
interface XDebuggerCustomMuteBreakpointHandler {
  companion object {
    internal val EP_NAME = ExtensionPointName<XDebuggerCustomMuteBreakpointHandler>("com.intellij.xdebugger.customMuteBreakpointHandler")
  }

  fun updateBreakpointsState(project: Project, event: AnActionEvent, muted: Boolean)

  fun areBreakpointsMuted(project: Project, event: AnActionEvent): Boolean

  fun canHandleMuteBreakpoints(project: Project, event: AnActionEvent): Boolean
}

internal fun getAvailableCustomMuteBreakpointHandler(project: Project, event: AnActionEvent): XDebuggerCustomMuteBreakpointHandler? {
  return EP_NAME.extensionList.firstOrNull { it.canHandleMuteBreakpoints(project, event) }
}