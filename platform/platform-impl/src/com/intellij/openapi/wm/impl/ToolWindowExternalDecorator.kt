// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindowType
import com.intellij.openapi.wm.WindowInfo

internal interface ToolWindowExternalDecorator {

  fun getToolWindowType(): ToolWindowType

  fun apply(info: WindowInfo)

  companion object {
    @JvmField val DECORATOR_PROPERTY: Key<ToolWindowExternalDecorator> = Key.create("ToolWindowExternalDecorator")
  }

}
