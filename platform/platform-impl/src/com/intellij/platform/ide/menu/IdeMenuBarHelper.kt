// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.menu

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.ide.DataManager
import com.intellij.ide.ui.UISettingsListener
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.actionSystem.impl.ActionMenu
import com.intellij.openapi.actionSystem.impl.MenuItemPresentationFactory
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.wm.WindowManager
import com.intellij.platform.diagnostic.telemetry.impl.rootTask
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.jetbrains.concurrency.await
import java.awt.Dialog
import java.awt.Dimension
import java.awt.KeyboardFocusManager
import java.util.concurrent.atomic.AtomicLong
import javax.swing.JComponent
import javax.swing.JFrame
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration.Companion.seconds

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

internal sealed class IdeMenuBarHelper(@JvmField val flavor: IdeMenuFlavor,
                                       @JvmField internal val menuBar: MenuBarImpl) : ActionAwareIdeMenuBar {
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
    val app = ApplicationManager.getApplication()
    val coroutineScope = menuBar.coroutineScope
    @Suppress("IfThenToSafeAccess")
    if (app != null) {
      app.messageBus.connect(coroutineScope).subscribe(UISettingsListener.TOPIC, UISettingsListener {
        check(updateRequests.tryEmit(true))
      })
    }

    coroutineScope.launch(ModalityState.any().asContextElement()) {
      withContext(if (StartUpMeasurer.isEnabled()) (rootTask() + CoroutineName("ide menu bar actions init")) else EmptyCoroutineContext) {
        val actions = updateMenuActions(mainActionGroup = menuBar.getMainMenuActionGroup(), forceRebuild = false, isFirstUpdate = true)
        postInitActions(actions)
      }

      val actionManager = serviceAsync<ActionManager>()
      if (actionManager is ActionManagerEx) {
        coroutineScope.launch {
          actionManager.timerEvents.collect {
            updateOnTimer()
          }
        }
      }

      val lastUpdate = AtomicLong(System.currentTimeMillis())
      updateRequests
        .collect { forceRebuild ->
          // as ActionManagerImpl TIMER_DELAY
          if ((System.currentTimeMillis() - lastUpdate.get()) < 500) {
            return@collect
          }

          if (!withContext(Dispatchers.EDT) { filterUpdate() }) {
            return@collect
          }

          presentationFactory.reset()
          updateMenuActions(mainActionGroup = menuBar.getMainMenuActionGroup(), forceRebuild = forceRebuild, isFirstUpdate = false)
          lastUpdate.set(System.currentTimeMillis())
        }
    }
  }

  private fun filterUpdate(): Boolean {
    if (!menuBar.frame.isShowing || !menuBar.frame.isActive) {
      return false
    }

    // do not update when a popup menu is shown
    // (if a popup menu contains action which is also in the menu bar, it should not be enabled/disabled)
    if (isUpdateForbidden()) {
      return false
    }

    // don't update the toolbar if there is currently active modal dialog
    val window = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow
    return window !is Dialog || !window.isModal
  }

  private suspend fun updateOnTimer() {
    withContext(Dispatchers.EDT) {
      // don't update the toolbar if there is currently active modal dialog
      val window = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow
      if (window !is Dialog || !window.isModal) {
        updateRequests.emit(false)
      }
    }
  }

  final override fun updateMenuActions(forceRebuild: Boolean) {
    check(updateRequests.tryEmit(forceRebuild))
  }

  protected open suspend fun postInitActions(actions: List<ActionGroup>) {
  }

  abstract suspend fun updateMenuActions(mainActionGroup: ActionGroup?, forceRebuild: Boolean, isFirstUpdate: Boolean): List<ActionGroup>
}

@Suppress("unused")
private val firstUpdateFastTrackUpdateTimeout = 30.seconds.inWholeMilliseconds

internal suspend fun expandMainActionGroup(mainActionGroup: ActionGroup,
                                           menuBar: JComponent,
                                           frame: JFrame,
                                           presentationFactory: PresentationFactory,
                                           isFirstUpdate: Boolean): List<ActionGroup>? {
  try {
    val windowManager = serviceAsync<WindowManager>()
    return withContext(CoroutineName("expandMainActionGroup") + Dispatchers.EDT) {
      val targetComponent = windowManager.getFocusedComponent(frame) ?: menuBar
      val dataContext = Utils.wrapToAsyncDataContext(DataManager.getInstance().getDataContext(targetComponent))
      Utils.expandActionGroupAsync(group = mainActionGroup,
                                   presentationFactory = presentationFactory,
                                   context = dataContext,
                                   place = ActionPlaces.MAIN_MENU,
                                   isToolbarAction = false,
                                   fastTrack = isFirstUpdate)
    }.await().filterIsInstance<ActionGroup>()
  }
  catch (e: ProcessCanceledException) {
    if (isFirstUpdate) {
      logger<IdeMenuBarHelper>().warn("Cannot expand action group")
    }

    // don't repeat - will do on next timer event
    return null
  }
}

internal suspend fun getAndWrapMainMenuActionGroup(): ActionGroup? {
  val group = CustomActionsSchema.getInstanceAsync().getCorrectedActionAsync(IdeActions.GROUP_MAIN_MENU) ?: return null
  // enforce the "always-visible" flag for all main menu items
  // without forcing everyone to employ custom groups in their plugin.xml files.
  return object : ActionGroupWrapper(group) {
    override fun getChildren(e: AnActionEvent?): Array<out AnAction> {
      return super.getChildren(e).onEach { it.templatePresentation.putClientProperty(ActionMenu.ALWAYS_VISIBLE, true) }
    }
  }
}