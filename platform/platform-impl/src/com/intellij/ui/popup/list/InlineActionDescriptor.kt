// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.popup.list

class InlineActionDescriptor(private val runnable: Runnable, val closesPopup: Boolean) {
  fun executeAction() = runnable.run()
}