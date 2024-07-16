// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.popup.list

import com.intellij.openapi.actionSystem.KeepPopupOnPerform

class InlineActionDescriptor(private val runnable: Runnable,
                             val keepPopupOnPerform: KeepPopupOnPerform) {
  fun executeAction(): Unit = runnable.run()
}