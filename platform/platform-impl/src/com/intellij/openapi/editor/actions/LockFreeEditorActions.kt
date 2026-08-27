// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("RedundantIf")

package com.intellij.openapi.editor.actions

import com.intellij.openapi.editor.actionSystem.LockFreeEditorActionsCore
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.util.PlatformUtils
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
// object for avoiding pollution of the global namespace
object LockFreeEditorActions {

  @JvmStatic
  fun needLockForArrowActions(): Boolean {
    if (LockFreeEditorActionsCore.overrideNeedLockForArrowActions) {
      return true
    }
    if (!canUseLockFreeActionsInCurrentProductMode()) {
      return true
    }
    return Registry.`is`("actions.update.and.perform.arrow.actions.with.rw.lock")
  }

  /**
   * Remote development has a quite sophisticated way of updating actions
   * Too much of lock-protected entities are accessed during action update,
   * so for the time being we disable the possibility of lock-free update in remote development.
   * See IJPL-250526
   * Also, rider and clion are also opted-out, as they use the same action update algorithm
   */
  private fun canUseLockFreeActionsInCurrentProductMode(): Boolean {
    return IdeProductMode.isMonolith && !PlatformUtils.isRider() && !PlatformUtils.isCLion()
  }
}

