// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.ui.cloneDialog

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vcs.CheckoutProvider
import com.intellij.util.EventDispatcher
import com.intellij.util.concurrency.annotations.RequiresEdt
import javax.swing.JComponent

/**
 * Main component for get-from-vcs extensions.
 * It's responsible for:
 * 1. Providing extension-specific UI for get-from-vcs dialog by [getView]
 * 2. handling commands that would be provided from top-level [com.intellij.openapi.ui.DialogWrapper]
 */
abstract class VcsCloneDialogExtensionComponent : Disposable {
  private val listeners = EventDispatcher.create(VcsCloneDialogComponentStateListener::class.java)

  protected val dialogStateListener = listeners.multicaster

  /**
   * Return main [JComponent] that will be displayed in center of get-from-vcs dialog when extension is selected.
   *
   * It is called once when extension is selected first time
   */
  @RequiresEdt
  abstract fun getView(): JComponent

  /**
   * Performs primary clone/checkout action. [doClone] is called from the UI-thread, but internal heavy clone task should be scheduled
   * in background
   */
  @RequiresEdt
  abstract fun doClone(checkoutListener: CheckoutProvider.Listener)

  /**
   * would be called from [com.intellij.openapi.ui.DialogWrapper.doValidateAll] when extension is selected
   *
   * @see com.intellij.openapi.ui.DialogWrapper.doValidateAll
   */
  @RequiresEdt
  abstract fun doValidateAll(): List<ValidationInfo>

  /**
   * would be called from [com.intellij.openapi.ui.DialogWrapper.getPreferredFocusedComponent] when clone dialog is opened
   *
   * @see com.intellij.openapi.ui.DialogWrapper.getPreferredFocusedComponent
   */
  @RequiresEdt
  open fun getPreferredFocusedComponent(): JComponent? = null

  /**
   * Adds listener that would be called from component in order to notify parent dialog about internal state
   *
   * @see VcsCloneDialogComponentStateListener
   */
  @RequiresEdt
  fun addComponentStateListener(componentStateListener: VcsCloneDialogComponentStateListener) {
    listeners.addListener(componentStateListener)
  }

  @RequiresEdt
  fun removeComponentListener(componentStateListener: VcsCloneDialogComponentStateListener) {
    listeners.removeListener(componentStateListener)
  }

  @RequiresEdt
  abstract fun onComponentSelected()

  final override fun dispose() = listeners.listeners.clear()
}
