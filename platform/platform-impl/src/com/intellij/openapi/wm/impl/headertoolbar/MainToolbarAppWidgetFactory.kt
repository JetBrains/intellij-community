// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.headertoolbar

import com.intellij.openapi.extensions.ExtensionPointName
import javax.swing.JComponent

interface MainToolbarAppWidgetFactory : MainToolbarWidgetFactory {
  companion object {
    val EP_NAME = ExtensionPointName<MainToolbarAppWidgetFactory>("com.intellij.appToolbarWidget")
  }

  fun createWidget(): JComponent
}