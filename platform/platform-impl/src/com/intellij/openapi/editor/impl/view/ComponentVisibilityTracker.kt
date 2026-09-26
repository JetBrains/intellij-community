// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

import com.intellij.openapi.Disposable
import com.intellij.util.cancelOnDispose
import com.intellij.util.ui.launchOnShow
import kotlinx.coroutines.awaitCancellation
import org.jetbrains.annotations.ApiStatus
import java.awt.Component

@ApiStatus.Internal
class ComponentVisibilityTracker(private val component: Component) {
  fun isShowing(): Boolean = component.isShowing

  fun runWhenHidden(parentDisposable: Disposable, action: Runnable) {
    component.launchOnShow("ComponentVisibilityTracker") {
      try {
        awaitCancellation()
      } finally {
        action.run()
      }
    }.cancelOnDispose(parentDisposable)
  }
}
