// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.context

import com.intellij.ui.ComponentUtil
import java.awt.Component
import java.awt.Window

internal class PromptWindowVisibilityController(private val promptWindow: PromptWindowHandle?) {
  private var promptWindowWasVisible: Boolean = false

  fun hide() {
    promptWindowWasVisible = promptWindow?.isVisible == true
    if (promptWindowWasVisible) {
      promptWindow?.isVisible = false
    }
  }

  fun restore() {
    if (!promptWindowWasVisible) return

    promptWindowWasVisible = false
    promptWindow?.isVisible = true
    promptWindow?.toFront()
    promptWindow?.requestFocus()
  }

  companion object {
    fun fromComponent(anchorComponent: Component): PromptWindowVisibilityController {
      return PromptWindowVisibilityController(ComponentUtil.getWindow(anchorComponent)?.let(::AwtPromptWindowHandle))
    }
  }
}

internal interface PromptWindowHandle {
  var isVisible: Boolean

  fun toFront()

  fun requestFocus()
}

private class AwtPromptWindowHandle(private val delegate: Window) : PromptWindowHandle {
  override var isVisible: Boolean
    get() = delegate.isVisible
    set(value) {
      delegate.isVisible = value
    }

  override fun toFront() {
    delegate.toFront()
  }

  override fun requestFocus() {
    delegate.requestFocus()
  }
}
