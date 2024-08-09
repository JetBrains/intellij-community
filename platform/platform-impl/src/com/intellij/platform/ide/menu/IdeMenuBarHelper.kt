// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.menu

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.ide.DataManager
import com.intellij.ide.ui.UISettingsListener
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.actionSystem.ex.ActionRuntimeRegistrar
import com.intellij.openapi.actionSystem.impl.*
import com.intellij.openapi.application.*
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.wm.WindowManager
import com.intellij.platform.diagnostic.telemetry.impl.rootTask
import com.intellij.platform.util.coroutines.flow.throttle
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.annotations.ApiStatus
import java.awt.Dialog
import java.awt.Dimension
import java.awt.KeyboardFocusManager
import javax.swing.JComponent
import javax.swing.JFrame

internal interface ActionAwareIdeMenuBar {
  fun updateMenuActions(forceRebuild: Boolean = false)
}

internal interface IdeMenuFlavor {
  val state: IdeMenuBarState
    get() = IdeMenuBarState.EXPANDED

  fun jMenuSelectionChanged(isIncluded: Boolean) {
  }

  fun getPreferredSize(size: Dimension): Dimension = size

  fun updateAppMenu()

  fun layoutClockPanelAndButton() {
  }

  fun correctMenuCount(menuCount: Int): Int = menuCount

  fun suspendAnimator() {}
}

private val LOG = logger<IdeMenuBarHelper>()

internal sealed class IdeMenuBarHelper(@JvmField val flavor: IdeMenuFlavor,
                                       @JvmField val menuBar: MenuBarImpl) : ActionAwareIdeMenuBar {
  protected abstract fun isUpdateForbidden(): Boolean

  @JvmField
  protected var visibleActions = emptyList<ActionGroup>()

  @JvmField
  protected val presentationFactory: PresentationFactory = MenuItemPresentationFactory()

  private val updateRequests = MutableSharedFlow<Boolean>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  interface MenuBarImpl {
    val frame: JFrame

    val coroutineScope: CoroutineScope
    val component: JComponent

    fun updateGlobalMenuRoots()

    suspend fun getMainMenuActionGroup(): ActionGroup?
  }

  init {
    val coroutineScope = menuBar.coroutineScope + CoroutineName("IdeMenuBarHelper")
    ApplicationManager.getApplication()?.messageBus?.connect(coroutineScope)?.subscribe(UISettingsListener.TOPIC, UISettingsListener {
      presentationFactory.reset()
      updateMenuActions(forceRebuild = true)
    })
    var context = Dispatchers.EDT + ModalityState.any().asContextElement()
    if (StartUpMeasurer.isEnabled()) {
      context += rootTask() + CoroutineName("ide menu bar actions init")
    }
    val initJob = coroutineScope.launch(context) {
      val actions = expandMainActionGroup(isFirstUpdate = true)
      doUpdateVisibleActions(newVisibleActions = actions, forceRebuild = false)
    }
    initJob.invokeOnCompletion { error ->
      if (error != null) {
        LOG.info("First menu bar update failed with $error")
      }
    }

    coroutineScope.launch {
      val timerEvents = (serviceAsync<ActionManager>() as? ActionManagerEx)?.timerEvents ?: return@launch
      timerEvents.collect {
        updateRequests.tryEmit(false)
      }
    }

    coroutineScope.launch {
      initJob.join()

      withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        updateRequests.throttle(500).collectLatest { forceRebuild ->
          runCatching {
            if (canUpdate()) {
              doUpdateVisibleActions(newVisibleActions = expandMainActionGroup(isFirstUpdate = false), forceRebuild = forceRebuild)
            }
          }.getOrLogException(LOG)
        }
      }
    }
  }

  private suspend fun expandMainActionGroup(isFirstUpdate: Boolean): List<ActionGroup> {
    val mainActionGroup = menuBar.getMainMenuActionGroup() ?: return emptyList()
    return expandMainActionGroup(
      mainActionGroup = mainActionGroup,
      menuBar = menuBar.component,
      frame = menuBar.frame,
      presentationFactory = presentationFactory,
      isFirstUpdate = isFirstUpdate,
    )
  }

  private fun canUpdate(): Boolean {
    ThreadingAssertions.assertEventDispatchThread()

    val focusedWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow
    val frame = focusedWindow ?: menuBar.frame
    if (!frame.isShowing || !frame.isActive) {
      return false
    }

    // do not update when a popup menu is shown
    // (if a popup menu contains an action which is also in the menu bar, it should not be enabled/disabled)
    if (isUpdateForbidden()) {
      return false
    }

    // don't update the toolbar if there is currently active modal dialog
    return focusedWindow !is Dialog || !focusedWindow.isModal
  }

  @RequiresEdt
  final override fun updateMenuActions(forceRebuild: Boolean) {
    if (forceRebuild && LOG.isDebugEnabled) {
      LOG.debug(Throwable("Force rebuild menu bar"))
    }
    check(updateRequests.tryEmit(forceRebuild))
  }

  @RequiresEdt
  protected abstract suspend fun doUpdateVisibleActions(newVisibleActions: List<ActionGroup>, forceRebuild: Boolean)
}

private suspend fun expandMainActionGroup(mainActionGroup: ActionGroup,
                                          menuBar: JComponent,
                                          frame: JFrame,
                                          presentationFactory: PresentationFactory,
                                          isFirstUpdate: Boolean): List<ActionGroup> {
  ThreadingAssertions.assertEventDispatchThread()
  val windowManager = serviceAsync<WindowManager>()
  val dataManager = serviceAsync<DataManager>()
  return withContext(CoroutineName("expandMainActionGroup")) {
    val targetComponent = windowManager.getFocusedComponent(frame) ?: menuBar
    val dataContext =  writeIntentReadAction { dataManager.getDataContext(targetComponent) }
    Utils.expandActionGroupSuspend(
      group = mainActionGroup,
      presentationFactory = presentationFactory,
      dataContext = dataContext,
      place = ActionPlaces.MAIN_MENU,
      isToolbarAction = false,
      fastTrack = isFirstUpdate,
    )
  }.filterIsInstance<ActionGroup>()
}

@ApiStatus.Internal
suspend fun createIdeMainMenuActionGroup(): ActionGroup? {
  val group = withContext(Dispatchers.Default) {
    CustomActionsSchema.getInstanceAsync().getCorrectedActionAsync(IdeActions.GROUP_MAIN_MENU)
  } ?: return null
  return object : ActionGroupWrapper(group) {
    override fun postProcessVisibleChildren(e: AnActionEvent, visibleChildren: List<AnAction>): List<AnAction> {
      return super.postProcessVisibleChildren(e, visibleChildren)
        .filterIsInstance<ActionGroup>()
    }
  }
}

class IdeMainMenuActionCustomizer : ActionConfigurationCustomizer, ActionConfigurationCustomizer.LightCustomizeStrategy {
  // enforce the "always-visible" flag for all main menu items
  // without forcing everyone to employ custom groups in their plugin.xml files.
  override suspend fun customize(actionRegistrar: ActionRuntimeRegistrar) {
    val group = actionRegistrar.getActionOrStub(IdeActions.GROUP_MAIN_MENU) as? DefaultActionGroup
    group?.childActionsOrStubs?.forEach {
      it.templatePresentation.putClientProperty(ActionMenu.ALWAYS_VISIBLE, true)
    }
  }
}
