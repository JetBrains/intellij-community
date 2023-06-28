// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(FlowPreview::class)

package com.intellij.openapi.wm.impl

import com.intellij.diagnostic.subtask
import com.intellij.ide.DataManager
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.mac.screenmenu.MenuBar
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import org.jetbrains.concurrency.asDeferred
import java.awt.Component
import java.awt.Dialog
import java.awt.KeyboardFocusManager
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.MenuSelectionManager
import javax.swing.SwingUtilities
import kotlin.time.Duration.Companion.milliseconds

internal interface ActionAwareIdeMenuBar {
  suspend fun updateMenuActions(forceRebuild: Boolean = false)
}

internal interface IdeMenuFlavor {
  var state: IdeMenuBarState

  fun getProgress(): Double = 0.0

  fun addClockPanel() {}

  fun updateAppMenu()

  fun layoutClockPanelAndButton() {
  }

  fun correctMenuCount(menuCount: Int): Int = menuCount

  fun restartAnimator() {}

  fun suspendAnimator() {}
}

internal open class IdeMenuBarHelper(@JvmField val flavor: IdeMenuFlavor, @JvmField internal val menuBar: MenuBarImpl) {
  interface MenuBarImpl {
    val frame: JFrame

    val coroutineScope: CoroutineScope
    val isDarkMenu: Boolean
    val component: JComponent

    fun updateGlobalMenuRoots()

    suspend fun getMainMenuActionGroup(): ActionGroup?
  }

  @JvmField
  protected var visibleActions = emptyList<ActionGroup>()

  @JvmField
  protected val presentationFactory: MenuItemPresentationFactory = MenuItemPresentationFactory()

  private val updateRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  @Suppress("LeakingThis")
  private val timerListener = IdeMenuBarActionTimerListener(this, isWindowActive = {
    menuBar.frame.isShowing && menuBar.frame.isActive
  })

  init {
    val app = ApplicationManager.getApplication()
    @Suppress("IfThenToSafeAccess")
    if (app != null) {
      app.messageBus.connect(menuBar.coroutineScope).subscribe(UISettingsListener.TOPIC, UISettingsListener {
        check(updateRequests.tryEmit(Unit))
      })
    }

    menuBar.coroutineScope.launch {
      updateRequests
        .debounce(50.milliseconds)
        .collectLatest {
          withContext(Dispatchers.EDT) {
            presentationFactory.reset()
          }
          doUpdateMenuActions(mainActionGroup = menuBar.getMainMenuActionGroup(), forceRebuild = true)
        }
    }

    val job = scheduleUpdateActions()
    menuBar.coroutineScope.launch {
      job.join()

      val actionManager = ApplicationManager.getApplication().serviceAsync<ActionManager>()
      actionManager.addTimerListener(timerListener)
      menuBar.coroutineScope.coroutineContext.job.invokeOnCompletion {
        actionManager.removeTimerListener(timerListener)
      }
    }
  }

  private fun scheduleUpdateActions(): Job {
    return menuBar.coroutineScope.launch {
      launch {
        serviceAsync<CustomActionsSchema>()
      }

      subtask("ide menu bar actions init") {
        val actions = doUpdateMenuActions(mainActionGroup = menuBar.getMainMenuActionGroup(), forceRebuild = false)
        withContext(Dispatchers.EDT) {
          for (action in actions) {
            PopupMenuPreloader.install(menuBar.component, ActionPlaces.MAIN_MENU, null) { action }
          }
        }
      }
    }
  }

  suspend fun updateMenuActions(forceRebuild: Boolean = false) {
    val mainActionGroup = menuBar.getMainMenuActionGroup()
    doUpdateMenuActions(mainActionGroup = mainActionGroup, forceRebuild = forceRebuild)
  }

  open suspend fun doUpdateMenuActions(mainActionGroup: ActionGroup?, forceRebuild: Boolean): List<ActionGroup> {
    val menuBarComponent = menuBar.component
    val newVisibleActions = mainActionGroup?.let {
      expandMainActionGroup(mainActionGroup = it, menuBar = menuBarComponent, frame = menuBar.frame, presentationFactory = presentationFactory)
    } ?: emptyList()

    if (!forceRebuild && newVisibleActions == visibleActions && withContext(Dispatchers.EDT) { !presentationFactory.isNeedRebuild }) {
      val enableMnemonics = !UISettings.getInstance().disableMnemonics
      withContext(Dispatchers.EDT) {
        for (child in menuBarComponent.components) {
          if (child is ActionMenu) {
            child.updateFromPresentation(enableMnemonics)
          }
        }
      }
      return newVisibleActions
    }

    // should rebuild UI
    val changeBarVisibility = newVisibleActions.isEmpty() || visibleActions.isEmpty()
    withContext(Dispatchers.EDT) {
      visibleActions = newVisibleActions
      menuBarComponent.removeAll()
      createActionMenuList(newVisibleActions) {
        menuBarComponent.add(it)
      }
      presentationFactory.resetNeedRebuild()
      flavor.updateAppMenu()
      this@IdeMenuBarHelper.menuBar.updateGlobalMenuRoots()
      flavor.addClockPanel()
      menuBarComponent.validate()
      if (changeBarVisibility) {
        menuBarComponent.invalidate()
        (SwingUtilities.getAncestorOfClass(JFrame::class.java, menuBarComponent) as JFrame?)?.validate()
      }
    }
    return newVisibleActions
  }

  protected fun createActionMenuList(newVisibleActions: List<ActionGroup>, consumer: (ActionMenu) -> Unit) {
    if (newVisibleActions.isEmpty()) {
      return
    }

    val enableMnemonics = !UISettings.getInstance().disableMnemonics
    val isCustomDecorationActive = IdeFrameDecorator.isCustomDecorationActive()
    for (action in newVisibleActions) {
      val actionMenu = ActionMenu(null, ActionPlaces.MAIN_MENU, action, presentationFactory, enableMnemonics, menuBar.isDarkMenu, true)
      if (isCustomDecorationActive) {
        actionMenu.isOpaque = false
        actionMenu.isFocusable = false
      }
      consumer(actionMenu)
    }
  }
}

internal open class PeerBasedIdeMenuBarHelper(private val screenMenuPeer: MenuBar,
                                              flavor: IdeMenuFlavor,
                                              menuBar: MenuBarImpl) : IdeMenuBarHelper(flavor, menuBar) {
  override suspend fun doUpdateMenuActions(mainActionGroup: ActionGroup?, forceRebuild: Boolean): List<ActionGroup> {
    val newVisibleActions = mainActionGroup?.let {
      expandMainActionGroup(mainActionGroup = it,
                            menuBar = menuBar.component,
                            frame = menuBar.frame,
                            presentationFactory = presentationFactory)
    } ?: emptyList()

    if (!forceRebuild && newVisibleActions == visibleActions && withContext(Dispatchers.EDT) { !presentationFactory.isNeedRebuild}) {
      return newVisibleActions
    }

    withContext(Dispatchers.EDT) {
      visibleActions = newVisibleActions
      screenMenuPeer.beginFill()
      createActionMenuList(newVisibleActions) {
        screenMenuPeer.add(it.screenMenuPeer)
      }
      presentationFactory.resetNeedRebuild()
      screenMenuPeer.endFill()
      flavor.updateAppMenu()
    }
    return newVisibleActions
  }
}

private suspend fun expandMainActionGroup(mainActionGroup: ActionGroup,
                                          menuBar: Component,
                                          frame: JFrame,
                                          presentationFactory: PresentationFactory): List<ActionGroup> {
  return withContext(Dispatchers.EDT) {
    val targetComponent = WindowManager.getInstance().getFocusedComponent(frame) ?: menuBar
    val dataContext = Utils.wrapToAsyncDataContext(DataManager.getInstance().getDataContext(targetComponent))
    Utils.expandActionGroupAsync(/* group = */ mainActionGroup,
                                 /* presentationFactory = */ presentationFactory,
                                 /* context = */ dataContext,
                                 /* place = */ ActionPlaces.MAIN_MENU,
                                 /* isToolbarAction = */ false,
                                 /* skipFastTrack = */ false)
  }.asDeferred().await().filterIsInstance<ActionGroup>()
}

internal class IdeMenuBarActionTimerListener(private val menuBarHelper: IdeMenuBarHelper,
                                             private val isWindowActive: () -> Boolean) : TimerListener {
  override fun getModalityState() = ModalityState.stateForComponent(menuBarHelper.menuBar.component)

  override fun run() {
    if (!isWindowActive()) {
      return
    }

    // do not update when a popup menu is shown
    // (if a popup menu contains action which is also in the menu bar, it should not be enabled/disabled)
    val menuSelectionManager = MenuSelectionManager.defaultManager()
    val selectedPath = menuSelectionManager.selectedPath
    if (selectedPath.isNotEmpty()) {
      return
    }

    menuBarHelper.menuBar.coroutineScope.launch(Dispatchers.EDT) {
      // don't update the toolbar if there is currently active modal dialog
      val window = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow
      if (window !is Dialog || !window.isModal) {
        menuBarHelper.updateMenuActions()
      }
    }
  }
}

internal suspend fun getMainMenuActionGroup(frame: JFrame): ActionGroup? {
  val group = (frame.rootPane as? IdeRootPane)?.mainMenuActionGroup
  return group ?: serviceAsync<CustomActionsSchema>().getCorrectedAction(IdeActions.GROUP_MAIN_MENU) as ActionGroup?
}