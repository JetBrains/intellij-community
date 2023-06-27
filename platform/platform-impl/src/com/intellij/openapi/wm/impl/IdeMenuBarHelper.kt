// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(FlowPreview::class)

package com.intellij.openapi.wm.impl

import com.intellij.diagnostic.subtask
import com.intellij.ide.DataManager
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.ActionMenu
import com.intellij.openapi.actionSystem.impl.MenuItemPresentationFactory
import com.intellij.openapi.actionSystem.impl.PopupMenuPreloader
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.serviceAsync
import com.intellij.ui.mac.screenmenu.MenuBar
import com.intellij.util.IJSwingUtilities
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
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

  @JvmField protected var visibleActions = ArrayList<ActionGroup>()
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
            doUpdateMenuActions(mainActionGroup = menuBar.getMainMenuActionGroup(),
                                forceRebuild = true,
                                manager = ApplicationManager.getApplication().serviceAsync<ActionManager>(),
                                menuBar = menuBar.component)

          }
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
      val app = ApplicationManager.getApplication()
      launch {
        app.serviceAsync<CustomActionsSchema>()
      }

      val actionManager = app.serviceAsync<ActionManager>()

      subtask("ide menu bar actions init") {
        val mainActionGroup = menuBar.getMainMenuActionGroup()
        withContext(Dispatchers.EDT) {
          val actions = doUpdateMenuActions(mainActionGroup = mainActionGroup,
                                            forceRebuild = false,
                                            manager = actionManager,
                                            menuBar = menuBar.component)
          for (action in actions) {
            if (action is ActionGroup) {
              PopupMenuPreloader.install(menuBar.component, ActionPlaces.MAIN_MENU, null) { action }
            }
          }
        }
      }
    }
  }

  suspend fun updateMenuActions(forceRebuild: Boolean = false) {
    val mainActionGroup = menuBar.getMainMenuActionGroup()
    val actionManager = ApplicationManager.getApplication().serviceAsync<ActionManager>()
    withContext(Dispatchers.EDT) {
      doUpdateMenuActions(mainActionGroup = mainActionGroup,
                          forceRebuild = forceRebuild,
                          manager = actionManager,
                          menuBar = menuBar.component)
    }
  }


  open fun doUpdateMenuActions(mainActionGroup: ActionGroup?,
                               forceRebuild: Boolean,
                               manager: ActionManager,
                               menuBar: JComponent): List<AnAction> {
    val newVisibleActions = ArrayList<ActionGroup>()
    mainActionGroup?.let {
      expandMainActionGroup(mainActionGroup = it, menuBar = menuBar, newVisibleActions = newVisibleActions, manager = manager)
    }

    if (!forceRebuild && !presentationFactory.isNeedRebuild && newVisibleActions == visibleActions) {
      val enableMnemonics = !UISettings.getInstance().disableMnemonics
      for (child in menuBar.components) {
        if (child is ActionMenu) {
          child.updateFromPresentation(enableMnemonics)
        }
      }
      return newVisibleActions
    }

    // should rebuild UI
    val changeBarVisibility = newVisibleActions.isEmpty() || visibleActions.isEmpty()
    visibleActions = newVisibleActions
    menuBar.removeAll()
    createActionMenuList(newVisibleActions) {
      menuBar.add(it)
    }
    presentationFactory.resetNeedRebuild()
    flavor.updateAppMenu()
    this.menuBar.updateGlobalMenuRoots()
    flavor.addClockPanel()
    menuBar.validate()
    if (changeBarVisibility) {
      menuBar.invalidate()
      (SwingUtilities.getAncestorOfClass(JFrame::class.java, menuBar) as JFrame?)?.validate()
    }
    return newVisibleActions
  }

  protected fun expandMainActionGroup(mainActionGroup: ActionGroup,
                                      menuBar: Component,
                                      newVisibleActions: ArrayList<ActionGroup>,
                                      manager: ActionManager) {
    val targetComponent = IJSwingUtilities.getFocusedComponentInWindowOrSelf(menuBar)
    val dataContext = DataManager.getInstance().getDataContext(targetComponent)
    expandActionGroup(mainActionGroup = mainActionGroup,
                      context = dataContext,
                      newVisibleActions = newVisibleActions,
                      actionManager = manager,
                      presentationFactory = presentationFactory)
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
  override fun doUpdateMenuActions(mainActionGroup: ActionGroup?,
                                   forceRebuild: Boolean,
                                   manager: ActionManager,
                                   menuBar: JComponent): List<AnAction> {
    val newVisibleActions = ArrayList<ActionGroup>()
    mainActionGroup?.let {
      expandMainActionGroup(mainActionGroup = it, menuBar = menuBar, newVisibleActions = newVisibleActions, manager = manager)
    }

    if (!forceRebuild && !presentationFactory.isNeedRebuild && newVisibleActions == visibleActions) {
      return newVisibleActions
    }

    visibleActions = newVisibleActions
    screenMenuPeer.beginFill()
    createActionMenuList(newVisibleActions) {
      screenMenuPeer.add(it.screenMenuPeer)
    }
    presentationFactory.resetNeedRebuild()
    screenMenuPeer.endFill()
    flavor.updateAppMenu()
    return newVisibleActions
  }
}

private fun expandActionGroup(mainActionGroup: ActionGroup,
                              context: DataContext,
                              newVisibleActions: MutableList<ActionGroup>,
                              actionManager: ActionManager,
                              presentationFactory: MenuItemPresentationFactory) {
  // the only code that does not reuse ActionUpdater (do not repeat that anywhere else)
  val children = mainActionGroup.getChildren(null)
  for (action in children) {
    if (action !is ActionGroup) {
      continue
    }

    val presentation = presentationFactory.getPresentation(action)
    val e = AnActionEvent(null, context, ActionPlaces.MAIN_MENU, presentation, actionManager, 0)
    ActionUtil.performDumbAwareUpdate(action, e, false)
    if (presentation.isVisible) {
      newVisibleActions.add(action)
    }
  }
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