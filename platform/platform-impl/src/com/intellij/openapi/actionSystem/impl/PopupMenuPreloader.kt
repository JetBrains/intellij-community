// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl

import com.intellij.ide.DataManager
import com.intellij.ide.IdleTracker
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.Utils.createAsyncDataContext
import com.intellij.openapi.actionSystem.impl.Utils.freezeDataContext
import com.intellij.openapi.application.AccessToken
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.IdeFrame
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.platform.ide.menu.IdeJMenuBar
import com.intellij.ui.PopupHandler
import com.intellij.util.ArrayUtil
import com.intellij.util.IJSwingUtilities
import com.intellij.util.TimeoutUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.update.UiNotifyConnector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.Component
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import java.lang.ref.WeakReference
import java.util.function.Supplier
import javax.swing.JComponent
import javax.swing.JMenuBar
import javax.swing.SwingUtilities

private val LOG = logger<PopupMenuPreloader>()

private val MODE = Registry.get("actionSystem.update.actions.preload.menus").getSelectedOption()
private val PRELOADER_PLACE_SUFFIX = "(preload-$MODE)"

private const val MAX_RETRIES = 5
private const val MAX_EDITOR_PRELOADS = 4
private const val IDLE_LISTENER_DELAY = 2000
private const val MAX_PRELOAD_TIME = 60_000L

private var ourEditorContextMenuPreloadCount = 0


/**
 * Expands an action group for a warm-up as less intrusive as possible:
 * in an "idle" listener, on a UI-only data-context, with a dedicated action-place.
 *
 * Runs preloading with retries on first show, and on first focusGained.
 */
@Internal
class PopupMenuPreloader private constructor(component: JComponent,
                                             actionPlace: String,
                                             popupHandler: PopupHandler?,
                                             groupSupplier: () -> ActionGroup?) : HierarchyListener {
  private val place = actionPlace
  private val groupSupplier: () -> ActionGroup?
  private val myComponentRef = WeakReference(component)
  private val myPopupHandlerRef = if (popupHandler == null) null else WeakReference(popupHandler)
  private val myStarted = System.nanoTime()

  private var myRetries = 0
  private var job: Job? = null
  private var myDisposed = false

  private var removeIdleListener: AccessToken? = null

  init {
    this.groupSupplier = groupSupplier
    component.addHierarchyListener(this)
  }

  override fun hierarchyChanged(e: HierarchyEvent) {
    LOG.assertTrue(!myDisposed, "already disposed")
    if ((e.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong()) <= 0) return
    dispose("hierarchy changed")
  }

  private fun onIdle() {
    val component = myComponentRef.get()
    val popupHandler = myPopupHandlerRef?.get()
    if (component == null || !component.isShowing() ||
        myPopupHandlerRef != null && (
          popupHandler == null ||
          !ArrayUtil.contains(popupHandler, *component.mouseListeners))) {
      dispose("popupHandler removed")
      return
    }
    val actionGroup = groupSupplier()
    if (actionGroup == null) {
      dispose("action group not found")
      return
    }
    if (job?.isActive == true) {
      return
    }
    if (myRetries++ > MAX_RETRIES) {
      dispose("no retries left")
      return
    }
    val contextComponent: Component = if (ActionPlaces.MAIN_MENU == place) {
      IJSwingUtilities.getFocusedComponentInWindowOrSelf(component)
    }
    else {
      component
    }
    val dataContext = DataManager.getInstance().getDataContext(contextComponent)
    job = service<CoreUiCoroutineScopeHolder>().coroutineScope.launch(Dispatchers.EDT) {
      withTimeout(MAX_PRELOAD_TIME) {
        doPreload(actionGroup, dataContext)
      }
    }
  }

  private suspend fun doPreload(actionGroup: ActionGroup, dataContext: DataContext) {
    val dataContext = freezeDataContext(createAsyncDataContext(dataContext), null)
    val start = System.nanoTime()
    try {
      Utils.expandActionGroupSuspend(
        actionGroup, PresentationFactory(), dataContext,
        "$place$PRELOADER_PLACE_SUFFIX", false, false)
      dispose(TimeoutUtil.getDurationMillis(start))
    }
    catch (ex: Throwable) {
      throw ex
    }
  }

  @RequiresEdt
  private fun dispose(reason: Any) {
    if (myDisposed) {
      return
    }
    val millis = reason as? Long ?: -1
    val success = millis > 0

    myDisposed = true
    job?.apply {
      job = null
      cancel()
    }
    removeIdleListener?.apply {
      removeIdleListener = null
      close()
    }
    myComponentRef.get()?.apply {
      removeHierarchyListener(this@PopupMenuPreloader)
      if (success && this is EditorComponentImpl) {
        ourEditorContextMenuPreloadCount++
      }
    }
    val group = groupSupplier()
    val text = if (group == null) "" else
      (group.templateText ?: ActionManager.getInstance().getId(group) ?: "")
    if (success) {
      LOG.info("'$text' at '$place' is preloaded in $millis ms " +
               "(${TimeoutUtil.getDurationMillis(myStarted)} ms since showing)")
    }
    else {
      LOG.info("'$text' at '$place' failed to preloaded: $reason " +
               "(${TimeoutUtil.getDurationMillis(myStarted)} ms since showing)")
    }
  }

  companion object {

    @JvmStatic
    fun install(component: JComponent,
                actionPlace: String,
                popupHandler: PopupHandler?,
                groupSupplier: Supplier<out ActionGroup>) {
      if (ApplicationManager.getApplication().isUnitTestMode() || "none" == MODE) {
        return
      }
      if (component is EditorComponentImpl && ourEditorContextMenuPreloadCount > MAX_EDITOR_PRELOADS ||
          component is IdeJMenuBar && SwingUtilities.getWindowAncestor(component) is IdeFrame.Child) {
        return
      }
      val runnable = Runnable {
        if (popupHandler != null && !component.mouseListeners.contains(popupHandler) ||
            component is EditorComponentImpl && !EditorUtil.isRealFileEditor(component.editor)) {
          return@Runnable
        }
        val preloader = PopupMenuPreloader(component, actionPlace, popupHandler, groupSupplier::get)
        preloader.removeIdleListener = IdleTracker.getInstance().addIdleListener(IDLE_LISTENER_DELAY) {
          preloader.onIdle()
        }
      }
      // first-time preloading on first show
      UiNotifyConnector.doWhenFirstShown(component, runnable)
      if (component is JMenuBar) return
      // second-time preloading for a hopefully non-trivial selection
      component.addFocusListener(object : FocusAdapter() {
        override fun focusGained(e: FocusEvent) {
          component.removeFocusListener(this)
          runnable.run()
        }
      })
    }

    @JvmStatic
    fun isToSkipComputeOnEDT(place: String): Boolean {
      return place.endsWith(PRELOADER_PLACE_SUFFIX) && "bgt" == MODE
    }
  }
}
