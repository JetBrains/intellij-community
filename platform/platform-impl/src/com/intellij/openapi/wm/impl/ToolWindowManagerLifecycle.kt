// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(FlowPreview::class)
@file:Suppress("ReplaceGetOrSet")

package com.intellij.openapi.wm.impl

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.project.getOpenedProjects
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ToolWindowType
import com.intellij.ui.ComponentUtil
import com.intellij.ui.ExperimentalUI
import com.intellij.util.ui.FocusUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.AWTEvent
import java.awt.Component
import java.awt.Dialog
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.FocusEvent
import java.awt.event.KeyEvent
import java.awt.event.WindowEvent
import java.beans.PropertyChangeListener
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlin.time.Duration.Companion.milliseconds

private fun handleFocusEvent(event: FocusEvent) {
  if (event.id == FocusEvent.FOCUS_LOST) {
    // We're interested in the case when some other component gained focus permanently.
    // Therefore, we're not interested if:
    // 1. some component lost focus, but no other component gained it, or
    // 2. the other (opposite) component gained focus only temporarily.
    // 3. the component that gained focus is no longer showing (possible, because events arrive asynchronously).
    if (event.oppositeComponent == null || event.isTemporary || !event.oppositeComponent.isShowing) {
      return
    }

    val project = IdeFocusManager.getGlobalInstance().lastFocusedFrame?.project ?: return
    if (project.isDisposed || project.isDefault) {
      return
    }

    val toolWindowManager = ToolWindowManager.getInstance(project) as ToolWindowManagerImpl

    toolWindowManager.revalidateStripeButtons()

    if (Registry.`is`("auto.hide.all.tool.windows.on.focus.change", true)) {
      hideAllUnfocusedAutoHideToolWindows(toolWindowManager, event.oppositeComponent)
    }
    else {
      val toolWindowId = getToolWindowIdForComponent(event.component) ?: return
      hideIfAutoHideToolWindowLostFocus(toolWindowManager, toolWindowId, event.oppositeComponent)
    }

  }
  else if (event.id == FocusEvent.FOCUS_GAINED) {
    val component = event.component ?: return
    for (project in getOpenedProjects()) {
      for (composite in (FileEditorManagerEx.getInstanceExIfCreated(project) ?: return).activeSplittersComposites) {
        if (composite.allEditors.any { SwingUtilities.isDescendingFrom(component, it.component) }) {
          (ToolWindowManager.getInstance(project) as ToolWindowManagerImpl).activeStack.clear()
        }
      }
    }
  }
}

private fun hideAllUnfocusedAutoHideToolWindows(
  toolWindowManager: ToolWindowManagerImpl,
  focusedComponent: Component,
  predicate: (String) -> Boolean = { true },
) {
  for (id in toolWindowManager.idToEntry.keys) {
    if (predicate(id)) {
      hideIfAutoHideToolWindowLostFocus(toolWindowManager, id, focusedComponent)
    }
  }
}

private fun hideIfAutoHideToolWindowLostFocus(toolWindowManager: ToolWindowManagerImpl, toolWindowId: String, focusedComponent: Component) {
  val activeEntry = toolWindowManager.idToEntry[toolWindowId] ?: return
  val windowInfo = activeEntry.readOnlyWindowInfo
  // just removed
  if (!windowInfo.isVisible) {
    return
  }

  if (!(windowInfo.isAutoHide || windowInfo.type == ToolWindowType.SLIDING)) {
    return
  }

  // Not focused, but just requested focus, don't hide.
  // This is important when switching from one sliding tool window to another:
  // in this case, the editor temporarily gets focus, which may cause the newly shown tool window
  // to hide before it's even shown.
  if (activeEntry.toolWindow.isAboutToReceiveFocus) {
    return
  }

  // let's check that tool window actually loses focus
  val focusedToolWindowId = getToolWindowIdForComponent(focusedComponent)
  if (focusedToolWindowId != toolWindowId) {
    val focusGoesToPopup = JBPopupFactory.getInstance().getParentBalloonFor(focusedComponent) != null
    val focusGoesToDialog = focusedToolWindowId == null && ComponentUtil.getWindow(focusedComponent) is Dialog
    if (!focusGoesToPopup && !focusGoesToDialog) {
      val info = toolWindowManager.getRegisteredMutableInfoOrLogError(toolWindowId)
      toolWindowManager.deactivateToolWindow(info, activeEntry)
    }
  }
}

private inline fun process(processor: (manager: ToolWindowManagerImpl) -> Unit) {
  for (project in getOpenedProjects()) {
    processor(ToolWindowManager.getInstance(project) as ToolWindowManagerImpl)
  }
}

private class MyListener : AWTEventListener {
  override fun eventDispatched(event: AWTEvent?) {
    if (event is FocusEvent) {
      handleFocusEvent(event)
    }
    else if (event is WindowEvent && event.getID() == WindowEvent.WINDOW_LOST_FOCUS) {
      process { manager ->
        val frame = event.getSource() as? JFrame
        // Reset the hold state if a tool window owning frame is losing focus, and the window gaining focus isn't a tool window frame
        if (manager.toolWindowPanes.values.any { it.frame === frame }
            && manager.toolWindowPanes.values.all { it.frame !== event.oppositeWindow }) {
          manager.resetHoldState()
        }
      }
    }
  }
}

@Service(Service.Level.APP)
internal class ToolWindowManagerAppLevelHelper(coroutineScope: CoroutineScope) {
  init {
    val awtFocusListener = MyListener()
    Toolkit.getDefaultToolkit().addAWTEventListener(awtFocusListener, AWTEvent.FOCUS_EVENT_MASK or AWTEvent.WINDOW_FOCUS_EVENT_MASK)

    val updateHeadersRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    coroutineScope.launch {
      updateHeadersRequests
        .debounce(50.milliseconds)
        .collectLatest {
          for (project in getOpenedProjects()) {
            val toolWindowManager = project.serviceAsync<ToolWindowManager>() as ToolWindowManagerImpl
            withContext(Dispatchers.EDT) {
              toolWindowManager.updateToolWindowHeaders()
            }
          }
        }
    }
    val focusListener = PropertyChangeListener { check(updateHeadersRequests.tryEmit(Unit)) }
    FocusUtil.addFocusOwnerListener(ApplicationManager.getApplication(), focusListener)

    val connection = ApplicationManager.getApplication().messageBus.connect(coroutineScope)
    connection.subscribe(ProjectCloseListener.TOPIC, object : ProjectCloseListener {
      override fun projectClosingBeforeSave(project: Project) {
        val manager = (project.serviceIfCreated<ToolWindowManager>() as ToolWindowManagerImpl?) ?: return
        for (entry in manager.idToEntry.values) {
          manager.saveFloatingOrWindowedState(entry, manager.layoutState.getInfo(entry.id) ?: continue)
        }
      }

      override fun projectClosed(project: Project) {
        (project.serviceIfCreated<ToolWindowManager>() as ToolWindowManagerImpl?)?.projectClosed()
      }
    })

    connection.subscribe(KeymapManagerListener.TOPIC, object : KeymapManagerListener {
      override fun activeKeymapChanged(keymap: Keymap?) {
        process { manager ->
          manager.idToEntry.values.forEach {
            it.stripeButton?.updatePresentation()
          }
        }
      }
    })

    connection.subscribe(AnActionListener.TOPIC, object : AnActionListener {
      override fun beforeActionPerformed(action: AnAction, event: AnActionEvent) {
        process { manager ->
          if (manager.currentState != KeyState.HOLD) {
            manager.resetHoldState()
          }
          if (Registry.`is`("auto.hide.all.tool.windows.on.any.action", true)) {
            val focusedComponent = IdeFocusManager.getInstance(manager.project).focusOwner
            val actionComponent = event.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT) ?: event.inputEvent?.component
            val actionComponentWindow = ComponentUtil.getWindow(actionComponent)
            // Not the best heuristics, but there seems to be no easy way to check "Is this a popup?"
            // So we check for something like "javax.swing.Popup$HeavyweightWindow...".
            val actionInvokedFromPopup = actionComponentWindow?.javaClass?.name?.startsWith("javax.swing.Popup") == true
            val actionToolWindowId = getToolWindowIdForComponent(actionComponent)
            if (focusedComponent != null && !actionInvokedFromPopup) {
              hideAllUnfocusedAutoHideToolWindows(manager, focusedComponent) { id -> id != actionToolWindowId }
            }
          }
        }

        if (ExperimentalUI.isNewUI()) {
          if (event.place == ActionPlaces.TOOLWINDOW_TITLE) {
            val toolWindowManager = ToolWindowManager.getInstance(event.project!!) as ToolWindowManagerImpl
            val toolWindowId = event.dataContext.getData(PlatformDataKeys.TOOL_WINDOW)?.id ?: return
            toolWindowManager.activateToolWindow(toolWindowId, null, true)
          }

          if (event.place == ActionPlaces.TOOLWINDOW_POPUP) {
            val toolWindowManager = ToolWindowManager.getInstance(event.project!!) as ToolWindowManagerImpl
            val activeEntry = toolWindowManager.idToEntry.get(toolWindowManager.lastActiveToolWindowId ?: return) ?: return
            (activeEntry.toolWindow.decorator ?: return).headerToolbar.component.isVisible = true
          }
        }
      }
    })

    IdeEventQueue.getInstance().addDispatcher(
      object : IdeEventQueue.NonLockedEventDispatcher {
        override fun dispatch(e: AWTEvent): Boolean {
          if (e is KeyEvent) {
            process { manager ->
              manager.dispatchKeyEvent(e)
            }
          }

          return false
        }
      }, coroutineScope)
  }
}
