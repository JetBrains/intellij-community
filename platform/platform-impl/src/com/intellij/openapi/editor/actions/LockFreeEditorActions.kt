// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("RedundantIf")

package com.intellij.openapi.editor.actions

import com.intellij.openapi.editor.actionSystem.LockFreeEditorActionsCore
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.platform.rd.RdProtocolDetector
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
   * RD Protocol clients use lock-protected data when they update actions.
   * Lock-free updates remain disabled for these clients. See IJPL-250526.
   *
   * RD Protocol clients can also be loaded dynamically (i.e., C++ Nova plugin in IDEA), so static check for product mode is not enough
   */
  private fun canUseLockFreeActionsInCurrentProductMode(): Boolean {
    return IdeProductMode.isMonolith && !RdProtocolDetector.isRdProtocolDetected()
  }
}
