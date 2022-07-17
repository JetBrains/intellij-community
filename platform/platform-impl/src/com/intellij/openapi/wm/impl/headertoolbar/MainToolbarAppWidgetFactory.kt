// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.headertoolbar

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

/**
 * Factory for Application-level widgets placed in main toolbar.
 *
 * @see [MainToolbarWidgetFactory]
 */
@ApiStatus.Experimental
@ApiStatus.Internal
interface MainToolbarAppWidgetFactory : MainToolbarWidgetFactory {
  companion object {
    val EP_NAME = ExtensionPointName<MainToolbarAppWidgetFactory>("com.intellij.appToolbarWidget")
  }

  /**
   * Factory method to create widget
   */
  fun createWidget(): JComponent
}