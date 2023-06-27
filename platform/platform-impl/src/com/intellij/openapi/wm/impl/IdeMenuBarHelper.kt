// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(FlowPreview::class)

package com.intellij.openapi.wm.impl

import com.intellij.ide.DataManager
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.ActionMenu
import com.intellij.openapi.actionSystem.impl.MenuItemPresentationFactory
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceAsync
import com.intellij.ui.mac.screenmenu.MenuBar
import com.intellij.util.IJSwingUtilities
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import java.awt.Component
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlin.time.Duration.Companion.milliseconds

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

internal open class IdeMenuBarHelper(@JvmField val flavor: IdeMenuFlavor, @JvmField protected val menuBar: MenuBarImpl) {
  interface MenuBarImpl {
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

