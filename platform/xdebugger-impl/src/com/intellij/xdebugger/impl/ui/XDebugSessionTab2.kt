// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.ui

import com.intellij.debugger.ui.DebuggerContentInfo
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunnerLayoutUi
import com.intellij.execution.ui.layout.LayoutAttractionPolicy
import com.intellij.execution.ui.layout.PlaceInGrid
import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.ui.PersistentThreeComponentSplitter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.ui.JBColor
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.xdebugger.*
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.actions.XDebuggerActions
import com.intellij.xdebugger.impl.frame.XThreadsFramesView
import com.intellij.xdebugger.impl.frame.XVariablesView
import com.intellij.xdebugger.impl.frame.XWatchesViewImpl
import javax.swing.Icon

class XDebugSessionTab2(
  session: XDebugSessionImpl,
  icon: Icon?,
  environment: ExecutionEnvironment?
) : XDebugSessionTab(session, icon, environment, false) {

  companion object {
    private const val threadsIsVisibleKey = "threadsIsVisibleKey"
    private const val debuggerContentId = "DebuggerView"
  }

  private val project = session.project
  private var threadsIsVisible
    get() = PropertiesComponent.getInstance(project).getBoolean(threadsIsVisibleKey, true)
    set(value) = PropertiesComponent.getInstance(project).setValue(threadsIsVisibleKey, value, true)

  private val lifetime = Disposer.newDisposable()

  private val splitter = PersistentThreeComponentSplitter(false, true, "DebuggerViewTab", lifetime, project, 0.35f, 0.3f)
  private val xThreadsFramesView = XThreadsFramesView(myProject)

  private val toolWindow get() = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.DEBUG)

  init {
    // value from com.intellij.execution.ui.layout.impl.GridImpl
    splitter.setMinSize(48)

    session.addSessionListener(object : XDebugSessionListener {
      override fun sessionStopped() {
        UIUtil.invokeLaterIfNeeded {
          splitter.saveProportions()
          Disposer.dispose(lifetime)
        }
      }
    })

    project.messageBus.connect(lifetime).subscribe(XDebuggerManager.TOPIC, object : XDebuggerManagerListener {
      override fun processStarted(debugProcess: XDebugProcess) {
        UIUtil.invokeLaterIfNeeded {
          if (debugProcess.session != null && debugProcess.session != session) {
            splitter.saveProportions()
          }
        }
      }

      override fun currentSessionChanged(previousSession: XDebugSession?, currentSession: XDebugSession?) {
        UIUtil.invokeLaterIfNeeded {
          if (previousSession == session) {
            splitter.saveProportions()
            xThreadsFramesView.saveUiState()
          }
          else if (currentSession == session)
            splitter.restoreProportions()
        }
      }

      override fun processStopped(debugProcess: XDebugProcess) {
        UIUtil.invokeLaterIfNeeded {
          splitter.saveProportions()
          xThreadsFramesView.saveUiState()
          if (debugProcess.session == session)
            Disposer.dispose(lifetime)
        }
      }
    })
  }

  override fun getWatchesContentId() = debuggerContentId
  override fun getFramesContentId() = debuggerContentId

  override fun setWatchesInVariablesImpl() {
    val session = mySession ?: return

    unregisterView(DebuggerContentInfo.VARIABLES_CONTENT)
    unregisterView(DebuggerContentInfo.WATCHES_CONTENT)

    val watchesView = XWatchesViewImpl(session, isWatchesInVariables, true).apply {
      myWatchesView = this
      registerView(DebuggerContentInfo.WATCHES_CONTENT, this)
      attachViewToSession(session, this)
    }

    fun tryCreateVariables(session: XDebugSessionImpl) = if (isWatchesInVariables) null else XVariablesView(session)

    val variablesView = tryCreateVariables(session).apply {
      registerView(DebuggerContentInfo.VARIABLES_CONTENT, this ?: watchesView)
      attachViewToSession(session, this)
    }

    splitter.apply {
      innerComponent = variablesView?.panel
      lastComponent = watchesView.panel
    }

    UIUtil.removeScrollBorder(splitter)

    splitter.revalidate()
    splitter.repaint()

    session.rebuildViews()
  }

  override fun initListeners(ui: RunnerLayoutUi?) = Unit

  override fun initDebuggerTab(session: XDebugSessionImpl) {
    val framesView = xThreadsFramesView
    registerView(DebuggerContentInfo.FRAME_CONTENT, framesView)

    framesView.setThreadsVisible(threadsIsVisible)
    splitter.firstComponent = xThreadsFramesView.mainPanel
    setWatchesInVariablesImpl()

    val name = debuggerContentId
    val content = myUi.createContent(name, splitter, XDebuggerBundle.message("xdebugger.debugger.tab.title"), null, framesView.defaultFocusedComponent).apply {
      isCloseable = false
    }

    myUi.addContent(content, 0, PlaceInGrid.left, false)

    ui.defaults.initContentAttraction(debuggerContentId, XDebuggerUIConstants.LAYOUT_VIEW_BREAKPOINT_CONDITION, LayoutAttractionPolicy.FocusOnce())

    toolWindow?.let {
      val contentManager = it.contentManager
      val listener = object : ContentManagerListener {
        override fun contentAdded(event: ContentManagerEvent) {
          setHeaderState()
        }

        override fun contentRemoved(event: ContentManagerEvent) {
          setHeaderState()
        }
      }
      contentManager.addContentManagerListener(listener)
      Disposer.register(lifetime, Disposable {
        contentManager.removeContentManagerListener(listener)
      })
    }

    setHeaderState()
  }

  private fun setHeaderState() {
    toolWindow?.let { toolWindow ->
      if (toolWindow !is ToolWindowEx) return@let

      val singleContent = toolWindow.contentManager.contents.singleOrNull()
      val toolbar = DefaultActionGroup().apply {
        if (singleContent == null) return@apply

        add(object : AnAction() {
          override fun actionPerformed(e: AnActionEvent) {
            toolWindow.contentManager.removeContent(singleContent, true)
          }

          override fun update(e: AnActionEvent) {
            e.presentation.text = "Close"
            e.presentation.icon = AllIcons.Actions.Close
          }
        })
        addAll(toolWindow.decorator.headerToolbar.actions)
      }
      myUi.options.setTopRightToolbar(toolbar, ActionPlaces.DEBUGGER_TOOLBAR)

      toolWindow.decorator.isHeaderVisible = singleContent == null

      if (toolWindow.decorator.isHeaderVisible) {
        toolWindow.component.border = null
        toolWindow.component.invalidate()
        toolWindow.component.repaint()
      } else if (toolWindow.component.border == null) {
        UIUtil.addBorder(toolWindow.component, JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0))
      }
    }
  }

  override fun registerAdditionalActions(leftToolbar: DefaultActionGroup, topLeftToolbar: DefaultActionGroup, settings: DefaultActionGroup) {
    leftToolbar.apply {
      val constraints = Constraints(Anchor.BEFORE, XDebuggerActions.VIEW_BREAKPOINTS)

      add(object : ToggleAction() {
        override fun setSelected(e: AnActionEvent, state: Boolean) {
          threadsIsVisible = state
          xThreadsFramesView.setThreadsVisible(state)
          Toggleable.setSelected(e.presentation, state)
        }

        override fun isSelected(e: AnActionEvent) = threadsIsVisible

        override fun update(e: AnActionEvent) {
          e.presentation.icon = AllIcons.Actions.SplitVertically
          if (threadsIsVisible) {
            e.presentation.text = "Hide threads view"
          }
          else {
            e.presentation.text = "Show threads view"
          }

          setSelected(e, threadsIsVisible)
        }
      }, constraints)

      add(ActionManager.getInstance().getAction(XDebuggerActions.FRAMES_TOP_TOOLBAR_GROUP), constraints)
      add(Separator.getInstance(), constraints)
    }

    super.registerAdditionalActions(leftToolbar, settings, topLeftToolbar)
  }

  override fun dispose() {
    Disposer.dispose(lifetime)
    super.dispose()
  }
}