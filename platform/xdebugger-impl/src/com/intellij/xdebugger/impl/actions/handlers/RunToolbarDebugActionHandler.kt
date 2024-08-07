// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions.handlers

import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.runToolbar.environment
import com.intellij.execution.runToolbar.isProcessTerminating
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunWidgetResumeManager
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
abstract class RunToolbarDebugActionHandler : DebuggerActionHandler() {
  override fun perform(project: Project, event: AnActionEvent) {
    val session = getSession(event)
    if (session is XDebugSessionImpl) {
      perform(session, event.dataContext)
    }
  }

  override fun isHidden(project: Project, event: AnActionEvent): Boolean {
    if (LightEdit.owns(project)) return true
    return getSession(event)?.let { session ->
      isHidden(session, event.dataContext)
    } ?: true
  }

  override fun isEnabled(project: Project, event: AnActionEvent): Boolean {
    return !event.isProcessTerminating()
  }

  protected open fun getSession(e: AnActionEvent): XDebugSessionImpl? {
    val project = e.project ?: return null
    return e.environment()?.contentToReuse?.let { contentToReuse ->
      getAppropriateSession(contentToReuse, project)
    }
  }

  protected fun getAppropriateSession(descriptor: RunContentDescriptor, project: Project): XDebugSessionImpl? {
    return XDebuggerManager.getInstance(project)
      ?.debugSessions
      ?.filter { it.runContentDescriptor == descriptor }
      ?.filterIsInstance<XDebugSessionImpl>()?.firstOrNull { !it.isStopped }
  }

  protected abstract fun isHidden(session: XDebugSessionImpl, dataContext: DataContext?): Boolean

  protected abstract fun perform(session: XDebugSessionImpl, dataContext: DataContext?)
}

internal class RunToolbarResumeActionHandler : RunToolbarDebugActionHandler() {
  override fun isHidden(session: XDebugSessionImpl, dataContext: DataContext?): Boolean {
    return !session.isPaused || session.isReadOnly
  }

  override fun perform(session: XDebugSessionImpl, dataContext: DataContext?) {
    session.resume()
  }
}

@Internal
open class RunToolbarPauseActionHandler : RunToolbarDebugActionHandler() {
  override fun isHidden(session: XDebugSessionImpl, dataContext: DataContext?): Boolean {
    return !session.isPauseActionSupported || session.isPaused
  }

  override fun perform(session: XDebugSessionImpl, dataContext: DataContext?) {
    session.pause()
  }
}

internal class InlineXDebuggerResumeHandler(private val conf: RunnerAndConfigurationSettings) : XDebuggerResumeHandler() {
  override fun getConfiguration(project: Project): RunnerAndConfigurationSettings = conf
}

internal open class XDebuggerResumeHandler : CurrentSessionXDebuggerResumeHandler() {
  override fun getSession(e: AnActionEvent): XDebugSessionImpl? {
    val project = e.project ?: return null
    val configuration = getConfiguration(project) ?: return null

    return RunWidgetResumeManager.getInstance(project).getDebugDescriptor(configuration)?.let { debugDescr ->
      getAppropriateSession(debugDescr, project)
    }
  }

  open fun getConfiguration(project: Project): RunnerAndConfigurationSettings? {
    return RunManager.getInstanceIfCreated(project)?.selectedConfiguration
  }
}

internal open class CurrentSessionXDebuggerResumeHandler : RunToolbarDebugActionHandler() {
  enum class State {
    RESUME,
    PAUSE
  }

  override fun perform(session: XDebugSessionImpl, dataContext: DataContext?) {
    if (session.isReadOnly || !session.isPauseActionSupported) return

    if (session.isPaused) session.resume() else session.pause()
  }

  fun getState(e: AnActionEvent): State? {
    val session = getSession(e) ?: return null
    return if (session.isPaused) State.RESUME else State.PAUSE
  }

  override fun isHidden(session: XDebugSessionImpl, dataContext: DataContext?): Boolean {
    dataContext?.getData(CommonDataKeys.PROJECT)?.let { pr ->
      RunWidgetResumeManager.getInstance(pr).let {
        return session.isReadOnly || !session.isPauseActionSupported
      }
    }
    return false
  }

  public override fun getSession(e: AnActionEvent): XDebugSessionImpl? {
    e.project?.let {
      val currentSession = XDebuggerManager.getInstance(it).currentSession
      if (currentSession is XDebugSessionImpl) {
        return currentSession
      }
    }
    return null
  }
}