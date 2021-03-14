// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.content.tabActions

import com.intellij.openapi.ui.popup.ActiveIcon
import com.intellij.openapi.util.NlsContexts

abstract class ContentTabAction(val icon: ActiveIcon) {
  open val afterText: Boolean = true

  abstract val available: Boolean
  abstract fun runAction()

  @get:NlsContexts.Tooltip
  open val tooltip: String? = null
}
