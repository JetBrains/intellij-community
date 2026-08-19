// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.driver.sdk.ui.remote

import com.intellij.driver.client.Remote
import com.intellij.driver.client.RequiresLockSemantics
import com.intellij.driver.model.LockSemantics
import com.intellij.driver.model.ui.UiActionResult
import com.intellij.driver.model.ui.UiClickRequest
import com.intellij.driver.model.ui.UiDragRequest
import com.intellij.driver.model.ui.UiFillRequest
import com.intellij.driver.model.ui.UiLocator
import com.intellij.driver.model.ui.UiNode
import com.intellij.driver.model.ui.UiSnapshot

@Remote("com.jetbrains.performancePlugin.remotedriver.UiAutomationService", plugin = REMOTE_ROBOT_MODULE_ID)
interface UiAutomationService {
  @RequiresLockSemantics(LockSemantics.NO_LOCK)
  fun snapshot(): UiSnapshot

  @RequiresLockSemantics(LockSemantics.NO_LOCK)
  fun find(locator: UiLocator, timeoutMs: Long): UiNode?

  @RequiresLockSemantics(LockSemantics.NO_LOCK)
  fun click(request: UiClickRequest): UiActionResult

  @RequiresLockSemantics(LockSemantics.NO_LOCK)
  fun fill(request: UiFillRequest): UiActionResult

  @RequiresLockSemantics(LockSemantics.NO_LOCK)
  fun drag(request: UiDragRequest): UiActionResult
}
