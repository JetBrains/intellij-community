// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")
package com.intellij.platform.impl.toolkit

import sun.awt.KeyboardFocusManagerPeerImpl
import java.awt.Component
import java.awt.Window
import java.awt.event.FocusEvent

object IdeKeyboardFocusManagerPeer: KeyboardFocusManagerPeerImpl() {
  private var focusOwner: Component? = null
  private var focusedWindow: Window? = null

  /**
   * In a recent build of JetBrains runtime, the signature changed from
   *      boolean deliverFocus(Component lightweightChild, Component target,
   *                        boolean temporary, boolean focusedWindowChangeAllowed, long time,
   *                        FocusEvent.Cause cause, Component currentFocusOwner)
   * to
   *      boolean deliverFocus(Component lightweightChild, Component target,
   *                        boolean highPriority,
   *                        FocusEvent.Cause cause, Component currentFocusOwner)
   *
   * https://github.com/JetBrains/JetBrainsRuntime/commit/1a9838082e3eb48d43e6bac6a412463923173fc7#diff-5818ad29e3f2e395597b8565f3553ad139de16439414e3fc688412fb35bd57f4
   */
  private val deliverFocusMethod = try {
    KeyboardFocusManagerPeerImpl::class.java.getMethod("deliverFocus",
                                                       Component::class.java, Component::class.java,
                                                       Boolean::class.java, Boolean::class.java, Long::class.java,
                                                       FocusEvent.Cause::class.java, Component::class.java)
  } catch (e: NoSuchMethodException) {
    null
  }

  private val deliverFocusMethodNew = try {
    KeyboardFocusManagerPeerImpl::class.java.getMethod("deliverFocus",
                                                       Component::class.java, Component::class.java,
                                                       Boolean::class.java,
                                                       FocusEvent.Cause::class.java, Component::class.java)
  } catch (e: NoSuchMethodException) {
    null
  }

  override fun setCurrentFocusedWindow(win: Window?) {
    focusedWindow = win
  }

  override fun getCurrentFocusedWindow(): Window? = focusedWindow

  override fun setCurrentFocusOwner(component: Component?) {
    focusOwner = component
  }

  override fun getCurrentFocusOwner(): Component? = focusOwner

  fun deliverFocus(
    current: Component?,
    lightweightChild: Component,
    window: Component,
    temporary: Boolean,
    focusedWindowChangeAllowed: Boolean,
    time: Long,
    cause: FocusEvent.Cause,
  ): Boolean {
    return deliverFocusMethod?.invoke(null, lightweightChild, window, temporary, focusedWindowChangeAllowed, time, cause, current) as Boolean? ?:
           deliverFocusMethodNew?.invoke(null, lightweightChild, window, true, cause, current) as Boolean? ?: false
  }

}