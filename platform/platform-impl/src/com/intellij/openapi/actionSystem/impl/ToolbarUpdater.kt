// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl

import com.intellij.openapi.actionSystem.TimerListener
import com.intellij.openapi.actionSystem.ex.ActionManagerEx.Companion.getInstanceEx
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManagerListener
import com.intellij.openapi.keymap.ex.KeymapManagerEx
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import com.intellij.util.ui.update.UiNotifyConnector.Companion.installOn
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.awt.Dialog
import java.awt.KeyboardFocusManager
import java.lang.ref.WeakReference
import javax.swing.JComponent
import javax.swing.MenuSelectionManager
import javax.swing.SwingUtilities

/**
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
abstract class ToolbarUpdater
@JvmOverloads constructor(
  private val component: JComponent,
  debugName: @NonNls String? = null
) : Activatable {
  private val myKeymapManagerListener = MyKeymapManagerListener()
  private val myTimerListener = MyTimerListener(this, debugName)

  private var myListenersArmed = false
  private var myInUpdate = false

  init {
    installOn(component, this)
  }

  override fun showNotify() {
    if (myListenersArmed) {
      return
    }

    myListenersArmed = true
    val actionManager = getInstanceEx()
    actionManager.addTimerListener(myTimerListener)
    KeymapManagerEx.getInstanceEx().addWeakListener(myKeymapManagerListener)
    updateActionTooltips()
  }

  override fun hideNotify() {
    if (!myListenersArmed) {
      return
    }

    myListenersArmed = false
    val actionManager = getInstanceEx()
    actionManager.removeTimerListener(myTimerListener)
    KeymapManagerEx.getInstanceEx().removeWeakListener(myKeymapManagerListener)
  }

  fun updateActions(now: Boolean, forced: Boolean, includeInvisible: Boolean) {
    if (myInUpdate) return
    val updateRunnable: Runnable = MyUpdateRunnable(this, forced, includeInvisible)
    val application = ApplicationManager.getApplication()
    if (now || application.isUnitTestMode() && application.isDispatchThread()) {
      updateRunnable.run()
    }
    else if (!application.isHeadlessEnvironment()) {
      if (application.isDispatchThread()) {
        updateRunnable.run()
      }
      else {
        UiNotifyConnector.doWhenFirstShown(component, updateRunnable)
      }
    }
  }

  protected abstract fun updateActionsImpl(forced: Boolean)

  protected fun updateActionTooltips() {
    UIUtil.uiTraverser(component)
      .preOrderDfsTraversal()
      .filter(ActionButton::class.java)
      .forEach { it.updateToolTipText() }
  }

  private inner class MyKeymapManagerListener : KeymapManagerListener {
    override fun activeKeymapChanged(keymap: Keymap?) {
      updateActionTooltips()
    }
  }

  private class MyTimerListener(
    updater: ToolbarUpdater,
    // input for curiosity
    @field:Suppress("unused") private val description: @NonNls String?
  ) : TimerListener {
    private val myReference = WeakReference(updater)

    override fun getModalityState(): ModalityState? {
      val updater = myReference.get() ?: return null
      return ModalityState.stateForComponent(updater.component)
    }

    override fun run() {
      val updater = myReference.get() ?: return

      if (!updater.component.isShowing()) {
        return
      }

      // do not update when a popup menu is shown
      // if the popup menu contains an action which is also in the toolbar, it should not be enabled/disabled
      val menuSelectionManager = MenuSelectionManager.defaultManager()
      val selectedPath = menuSelectionManager.getSelectedPath()
      if (selectedPath.size > 0) {
        return
      }

      // don't update the toolbar if there is currently active modal dialog
      val window = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow
      if (window is Dialog && window.isModal && !SwingUtilities.isDescendingFrom(updater.component, window)) {
        return
      }

      updater.updateActions(now = false, forced = false, includeInvisible = false)
    }
  }

  private class MyUpdateRunnable(
    updater: ToolbarUpdater,
    private val myForced: Boolean,
    private val myIncludeInvisible: Boolean
  ) : Runnable {
    private val myUpdaterRef = WeakReference(updater)
    private val myHash = updater.hashCode()

    override fun run() {
      val updater = myUpdaterRef.get() ?: return
      val component = updater.component
      if (!ApplicationManager.getApplication().isUnitTestMode() &&
          !UIUtil.isShowing(component) &&
          (!component.isDisplayable || !myIncludeInvisible)) {
        return
      }
      try {
        updater.myInUpdate = true
        updater.updateActionsImpl(myForced)
      }
      finally {
        updater.myInUpdate = false
      }
    }

    override fun equals(other: Any?): Boolean {
      if (other !is MyUpdateRunnable) return false

      if (myHash != other.myHash) return false

      val updater1 = myUpdaterRef.get()
      val updater2 = other.myUpdaterRef.get()
      return updater1 == updater2
    }

    override fun hashCode(): Int = myHash
  }
}
