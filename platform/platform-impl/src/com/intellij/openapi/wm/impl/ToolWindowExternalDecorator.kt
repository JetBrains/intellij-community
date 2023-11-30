// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindowType
import com.intellij.openapi.wm.WindowInfo
import java.awt.Component
import java.awt.Rectangle
import java.awt.Window

internal interface ToolWindowExternalDecorator {

  val window: Window

  val id: String

  fun getToolWindowType(): ToolWindowType

  fun apply(info: WindowInfo)

  // Swing stuff to be reused in both decorators through this interface:

  fun setLocationRelativeTo(parentFrame: Component?)

  var bounds: Rectangle

  fun log(): Logger

  companion object {
    @JvmField val DECORATOR_PROPERTY: Key<ToolWindowExternalDecorator> = Key.create("ToolWindowExternalDecorator")
  }

}
