// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.headertoolbar

import com.intellij.openapi.extensions.ExtensionPointName
import javax.swing.JComponent

interface MainToolbarWidgetFactory {

  companion object {
    val EP_NAME = ExtensionPointName.create<MainToolbarWidgetFactory>("com.intellij.toolbarWidget")
  }

  fun createWidget(): JComponent

  fun getPosition(): Position

  enum class Position {
    Left, Right, Center
  }
}