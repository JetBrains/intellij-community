// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.ui

import com.intellij.debugger.ui.DebuggerContentInfo
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.layout.LayoutAttractionPolicy
import com.intellij.execution.ui.layout.PlaceInGrid
import com.intellij.execution.ui.layout.impl.RunnerLayoutUiImpl
import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.PersistentThreeComponentSplitter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.openapi.wm.impl.content.SingleContentSupplier
import com.intellij.util.ui.UIUtil
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.actions.XDebuggerActions
import com.intellij.xdebugger.impl.frame.*
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode
import java.awt.Component
import java.awt.Container
import javax.swing.Icon
import javax.swing.LayoutFocusTraversalPolicy

//TODO: unify with XDebugSessionTab2
class XDebugSessionTab3(
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
  private val xThreadsFramesView = XFramesView(myProject)

  private var variables: XVariablesView? = null

  private val focusTraversalPolicy = MyFocusTraversalPolicy()

  private var mySingleContentSupplier: SingleContentSupplier? = null

  override fun getWatchesContentId() = debuggerContentId
  override fun getFramesContentId() = debuggerContentId

  override fun addVariablesAndWatches(session: XDebugSessionImpl) {
    val variablesView: XVariablesView?
    val watchesView: XVariablesView?
    if (isWatchesInVariables) {
      variablesView = XWatchesViewImpl(session, true, true, false)
      registerView(DebuggerContentInfo.VARIABLES_CONTENT, variablesView)
      variables = variablesView

      watchesView = null
      myWatchesView = variablesView
    } else {
      variablesView = XVariablesView(session)
      registerView(DebuggerContentInfo.VARIABLES_CONTENT, variablesView)
      variables = variablesView
      
      watchesView = XWatchesViewImpl(session, false, true, false)
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

    //framesView.setThreadsVisible(threadsIsVisible)
    splitter.firstComponent = xThreadsFramesView.mainPanel
    addVariablesAndWatches(session)

    val name = debuggerContentId
    val content = myUi.createContent(name, splitter, XDebuggerBundle.message("xdebugger.debugger.tab.title"), null, framesView.defaultFocusedComponent).apply {
      isCloseable = false
    }

    myUi.addContent(content, 0, PlaceInGrid.left, false)

    ui.defaults.initContentAttraction(debuggerContentId, XDebuggerUIConstants.LAYOUT_VIEW_BREAKPOINT_CONDITION, LayoutAttractionPolicy.FocusOnce())
  }
  private fun getComponents(): Iterator<Component> {
    return iterator {
      //if (threadsIsVisible)
      //  yield(xThreadsFramesView.threads)

      yield(xThreadsFramesView.mainPanel)
      val vars = variables ?: return@iterator

      yield(vars.defaultFocusedComponent)
      if (!isWatchesInVariables)
        yield(myWatchesView.defaultFocusedComponent)
    }
  }

  override fun initToolbars(session: XDebugSessionImpl) {
    (myUi as? RunnerLayoutUiImpl)?.setLeftToolbarVisible(false)
    val toolbar = DefaultActionGroup()
    toolbar.addAll(getCustomizedActionGroup(XDebuggerActions.TOOL_WINDOW_TOP_TOOLBAR_3_GROUP))

    // reversed because it was like this in the original tab
    for (action in session.restartActions.asReversed()) {
      toolbar.add(action, Constraints(Anchor.AFTER, IdeActions.ACTION_RERUN))
    }

    for (action in session.extraActions.asReversed()) {
      toolbar.add(action, Constraints(Anchor.AFTER, IdeActions.ACTION_STOP_PROGRAM))
    }

    for (action in session.extraStopActions) {
      toolbar.add(action, Constraints(Anchor.AFTER, IdeActions.ACTION_STOP_PROGRAM))
    }

    myUi.options.setTopLeftToolbar(toolbar, ActionPlaces.DEBUGGER_TOOLBAR)

    mySingleContentSupplier = object : RunTabSupplier(toolbar) {
      override fun getContentActions(): List<AnAction> {
        val settings = DefaultActionGroup(ActionsBundle.messagePointer("group.XDebugger.settings.text"), myUi.options.settingsActionsList.toList())
        registerAdditionalActions(DefaultActionGroup(), DefaultActionGroup(), settings)
        settings.isPopup = true
        settings.templatePresentation.icon = AllIcons.General.Settings
        return super.getContentActions() + settings
      }
    }
  }

  override fun getSupplier(): SingleContentSupplier? = mySingleContentSupplier

  override fun registerAdditionalActions(leftToolbar: DefaultActionGroup, topLeftToolbar: DefaultActionGroup, settings: DefaultActionGroup) {
    leftToolbar.apply {
      val constraints = Constraints(Anchor.BEFORE, XDebuggerActions.VIEW_BREAKPOINTS)

      add(object : ToggleAction() {
        override fun setSelected(e: AnActionEvent, state: Boolean) {
          if (threadsIsVisible != state) {
            threadsIsVisible = state
            updateTraversalPolicy()
          }
          //xThreadsFramesView.setThreadsVisible(state)
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
        else -> false
      }
    }
  }
}

internal class MorePopupGroup : DefaultActionGroup(), DumbAware {
  init {
    isPopup = true
    templatePresentation.icon = AllIcons.Actions.More
    templatePresentation.putClientProperty(ActionButton.HIDE_DROPDOWN_ICON, true)
  }
}