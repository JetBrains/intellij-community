// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.actions.handlers

import com.intellij.ide.lightEdit.LightEdit
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler
import com.intellij.xdebugger.impl.stateWidget.StateWidgetXDebugUtils.Companion.getSingleSession
import com.intellij.xdebugger.impl.stateWidget.StateWidgetXDebugUtils.Companion.isAvailable

abstract class StateWidgetDebugActionHandler : DebuggerActionHandler() {
  override fun perform(project: Project, event: AnActionEvent) {
    if (!isAvailable(project)) return
    val session = getSingleSession(project)
    if (session != null) {
      perform(session, event.dataContext)
    }
  }

  override fun isEnabled(project: Project, event: AnActionEvent): Boolean {
    if (LightEdit.owns(project)) return false
    if (!isAvailable(project)) return false
    val session = getSingleSession(project)
    return session != null && isEnabled(session, event.dataContext)
  }

  protected abstract fun isEnabled(session: XDebugSessionImpl, dataContext: DataContext?): Boolean

  protected abstract fun perform(session: XDebugSessionImpl, dataContext: DataContext?)
}

class StateWidgetResumeActionHandler : StateWidgetDebugActionHandler() {
  override fun isEnabled(session: XDebugSessionImpl, dataContext: DataContext?): Boolean {
    return session.isPaused
  }

  override fun perform(session: XDebugSessionImpl, dataContext: DataContext?) {
    session.resume()
  }
}

class StateWidgetPauseActionHandler : StateWidgetDebugActionHandler() {
  override fun isEnabled(session: XDebugSessionImpl, dataContext: DataContext?): Boolean {
    return session.isPauseActionSupported && !session.isPaused()
  }

  override fun perform(session: XDebugSessionImpl, dataContext: DataContext?) {
    session.pause()
  }
}