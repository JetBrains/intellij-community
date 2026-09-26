// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.frontend

import org.jetbrains.annotations.ApiStatus
import java.awt.event.InputEvent
import java.awt.event.KeyEvent

@ApiStatus.Internal
class SwitcherLaunchEventParameters(event: InputEvent?) {
  val wasAltDown: Boolean = true == event?.isAltDown
  val wasAltGraphDown: Boolean = true == event?.isAltGraphDown
  val wasControlDown: Boolean = true == event?.isControlDown
  val wasMetaDown: Boolean = true == event?.isMetaDown
  val isEnabled: Boolean = wasAltDown || wasAltGraphDown || wasControlDown || wasMetaDown

  val keyCode: Int? = (event as? KeyEvent)?.keyCode
}