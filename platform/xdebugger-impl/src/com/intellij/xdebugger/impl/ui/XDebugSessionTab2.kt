// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.ui

import com.intellij.debugger.ui.DebuggerContentInfo
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.layout.LayoutAttractionPolicy
import com.intellij.execution.ui.layout.PlaceInGrid
import com.intellij.icons.AllIcons
import com.intellij.ide.actions.TabListAction
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.ui.PersistentThreeComponentSplitter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ToolWindowType
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.JBColor
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.xdebugger.*
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.actions.XDebuggerActions
import com.intellij.xdebugger.impl.frame.*
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode
import java.awt.Component
import java.awt.Container
import javax.swing.Icon
import javax.swing.LayoutFocusTraversalPolicy
import javax.swing.event.AncestorEvent
import javax.swing.event.AncestorListener

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

  private var variables: XVariablesView? = null

  private val toolWindow get() = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.DEBUG)

  private val focusTraversalPolicy = MyFocusTraversalPolicy()

  init {
    // value from com.intellij.execution.ui.layout.impl.GridImpl
    splitter.setMinSize(48)

    splitter.isFocusCycleRoot = true
    splitter.isFocusTraversalPolicyProvider = true
    splitter.focusTraversalPolicy = focusTraversalPolicy

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

    val ancestorListener = object : AncestorListener {
      override fun ancestorAdded(event: AncestorEvent?) {
        if (XDebuggerManager.getInstance(project).currentSession == session) {
          splitter.restoreProportions()
        }
      }

      override fun ancestorRemoved(event: AncestorEvent?) {
        if (XDebuggerManager.getInstance(project).currentSession == session) {
          splitter.saveProportions()
          xThreadsFramesView.saveUiState()
        }
      }

      override fun ancestorMoved(event: AncestorEvent?) {
      }
    }

    toolWindow?.component?.addAncestorListener(ancestorListener)
    Disposer.register(lifetime, Disposable {
      toolWindow?.component?.removeAncestorListener(ancestorListener)
    })

    var oldToolWindowType: ToolWindowType? = null
    project.messageBus.connect(lifetime).subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
      override fun stateChanged(toolWindowManager: ToolWindowManager) {
        if (oldToolWindowType == toolWindow?.type) return

        setHeaderState()
        oldToolWindowType = toolWindow?.type
      }
    })
  }

  override fun getWatchesContentId() = debuggerContentId
  override fun getFramesContentId() = debuggerContentId

  private fun getWatchesViewImpl(session: XDebugSessionImpl, watchesIsVariables: Boolean): XWatchesViewImpl {
    val useSplitterView = (session.debugProcess as? XDebugSessionTabCustomizer)?.bottomLocalsComponentProvider != null
    return if (useSplitterView)
      XSplitterWatchesViewImpl(session, watchesIsVariables, true, withToolbar = true)
    else
      XWatchesViewImpl(session, watchesIsVariables, true, true)
  }

  override fun addVariablesAndWatches(session: XDebugSessionImpl) {
    val variablesView: XVariablesView?
    val watchesView: XVariablesView?
    if (isWatchesInVariables) {
      variablesView = getWatchesViewImpl(session, true)
      registerView(DebuggerContentInfo.VARIABLES_CONTENT, variablesView)
      variables = variablesView

      watchesView = null
      myWatchesView = variablesView
    } else {
      variablesView = XVariablesView(session)
      registerView(DebuggerContentInfo.VARIABLES_CONTENT, variablesView)
      variables = variablesView
      
      watchesView = getWatchesViewImpl(session, watchesIsVariables = false)
      registerView(DebuggerContentInfo.WATCHES_CONTENT, watchesView)
      myWatchesView = watchesView
    }

    splitter.apply {
      innerComponent = variablesView.panel
      lastComponent = watchesView?.panel
    }

    UIUtil.removeScrollBorder(splitter)

    splitter.revalidate()
    splitter.repaint()

    updateTraversalPolicy()
  }

  private fun updateTraversalPolicy() {
    focusTraversalPolicy.components = getComponents().asSequence().toList()
  }

  override fun initDebuggerTab(session: XDebugSessionImpl) {
    val framesView = xThreadsFramesView
    registerView(DebuggerContentInfo.FRAME_CONTENT, framesView)

    framesView.setThreadsVisible(threadsIsVisible)
    splitter.firstComponent = xThreadsFramesView.mainComponent
    addVariablesAndWatches(session)

    val name = debuggerContentId
    val content = myUi.createContent(name, splitter, XDebuggerBundle.message("xdebugger.debugger.tab.title"), AllIcons.Toolwindows.ToolWindowDebugger, framesView.defaultFocusedComponent).apply {
      isCloseable = false
    }

    myUi.addContent(content, 0, PlaceInGrid.left, false)

    ui.defaults
      .initContentAttraction(debuggerContentId, XDebuggerUIConstants.LAYOUT_VIEW_BREAKPOINT_CONDITION, LayoutAttractionPolicy.FocusOnce())
      .initContentAttraction(debuggerContentId, XDebuggerUIConstants.LAYOUT_VIEW_FINISH_CONDITION, LayoutAttractionPolicy.FocusOnce())

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
  private fun getComponents(): Iterator<Component> {
    return iterator {
      if (threadsIsVisible)
        yield(xThreadsFramesView.threads)

      yield(xThreadsFramesView.frames)
      val vars = variables ?: return@iterator

      yield(vars.defaultFocusedComponent)
      if (!isWatchesInVariables)
        yield(myWatchesView.defaultFocusedComponent)
    }
  }

  private fun setHeaderState() {
    toolWindow?.let { toolWindow ->
      if (toolWindow !is ToolWindowEx) return@let

      val singleContent = toolWindow.contentManager.contents.singleOrNull()
      val headerVisible = toolWindow.isHeaderVisible
      val topRightToolbar = DefaultActionGroup().apply {
        if (headerVisible) return@apply
        addAll(toolWindow.decorator.headerToolbar.actions.filter { it != null && it !is TabListAction })
      }
      myUi.options.setTopRightToolbar(topRightToolbar, ActionPlaces.DEBUGGER_TOOLBAR)

      val topMiddleToolbar = DefaultActionGroup().apply {
        if (singleContent == null || headerVisible) return@apply

        add(object : AnAction(XDebuggerBundle.message("session.tab.close.debug.session"), null, AllIcons.Actions.Close) {
          override fun actionPerformed(e: AnActionEvent) {
            toolWindow.contentManager.removeContent(singleContent, true)
          }
        })
        addSeparator()
      }
      myUi.options.setTopMiddleToolbar(topMiddleToolbar, ActionPlaces.DEBUGGER_TOOLBAR)

      toolWindow.decorator.isHeaderVisible = headerVisible

      if (toolWindow.decorator.isHeaderVisible) {
        toolWindow.component.border = null
        toolWindow.component.invalidate()
        toolWindow.component.repaint()
      } else if (toolWindow.component.border == null) {
        UIUtil.addBorder(toolWindow.component, JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0))
      }
    }
  }

  private val ToolWindowEx.isHeaderVisible get() = (type != ToolWindowType.DOCKED) || contentManager.contents.singleOrNull() == null

  override fun registerAdditionalActions(leftToolbar: DefaultActionGroup, topLeftToolbar: DefaultActionGroup, settings: DefaultActionGroup) {
    leftToolbar.apply {
      val constraints = Constraints(Anchor.BEFORE, XDebuggerActions.VIEW_BREAKPOINTS)

      add(object : ToggleAction() {
        override fun setSelected(e: AnActionEvent, state: Boolean) {
          if (threadsIsVisible != state) {
            threadsIsVisible = state
            updateTraversalPolicy()
          }
          xThreadsFramesView.setThreadsVisible(state)
          Toggleable.setSelected(e.presentation, state)
        }

        override fun isSelected(e: AnActionEvent) = threadsIsVisible

        override fun update(e: AnActionEvent) {
          e.presentation.icon = AllIcons.Actions.SplitVertically
          if (threadsIsVisible) {
            e.presentation.text = XDebuggerBundle.message("session.tab.hide.threads.view")
          }
          else {
            e.presentation.text = XDebuggerBundle.message("session.tab.show.threads.view")
          }

          setSelected(e, threadsIsVisible)
        }
      }, constraints)

      add(ActionManager.getInstance().getAction(XDebuggerActions.FRAMES_TOP_TOOLBAR_GROUP), constraints)
      add(Separator.getInstance(), constraints)
    }

    super.registerAdditionalActions(leftToolbar, topLeftToolbar, settings)
  }

  override fun dispose() {
    Disposer.dispose(lifetime)
    super.dispose()
  }

  class MyFocusTraversalPolicy : LayoutFocusTraversalPolicy() {
    var components: List<Component> = listOf()

    override fun getLastComponent(aContainer: Container?): Component {
      if (components.isNotEmpty())
        return components.last().prepare()

      return super.getLastComponent(aContainer)
    }

    override fun getFirstComponent(aContainer: Container?): Component {
      if (components.isNotEmpty())
        return components.first().prepare()

      return super.getFirstComponent(aContainer)
    }

    override fun getComponentAfter(aContainer: Container?, aComponent: Component?): Component {
      if (aComponent == null)
        return super.getComponentAfter(aContainer, aComponent)

      val index = components.indexOf(aComponent)
      if (index < 0 || index > components.lastIndex)
        return super.getComponentAfter(aContainer, aComponent)

      for (i in components.indices) {
        val component = components[(index + i + 1) % components.size]
        if (isEmpty(component)) continue

        return component.prepare()
      }

      return components[index + 1].prepare()
    }

    override fun getComponentBefore(aContainer: Container?, aComponent: Component?): Component {
      if (aComponent == null)
        return super.getComponentBefore(aContainer, aComponent)

      val index = components.indexOf(aComponent)
      if (index < 0 || index > components.lastIndex)
        return super.getComponentBefore(aContainer, aComponent)

      for (i in components.indices) {
        val component = components[(components.size + index - i - 1) % components.size]
        if (isEmpty(component)) continue

        return component.prepare()
      }

      return components[index - 1].prepare()
    }

    private fun Component.prepare(): Component {
      if (this is XDebuggerTree && this.selectionCount == 0){
        val child = root.children.firstOrNull() as? XDebuggerTreeNode ?: return this

        selectionPath = child.path
      }
      return this
    }

    private fun isEmpty(component: Component): Boolean {
      return when (component) {
        is XDebuggerThreadsList -> component.isEmpty
        is XDebuggerFramesList -> component.isEmpty
        is XDebuggerTree -> component.isEmpty
        else -> false;
      }
    }
  }
}