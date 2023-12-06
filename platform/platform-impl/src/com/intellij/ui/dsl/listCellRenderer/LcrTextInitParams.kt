// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer

import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.NamedColorUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Font
import javax.swing.UIManager

@ApiStatus.Experimental
class LcrTextInitParams(foreground: Color) : LcrInitParams() {

  /**
   * A grey text, that is usually used for non-primary information in renderers
   */
  val greyForeground: Color
    get() = NamedColorUtil.getInactiveTextColor()

  /**
   * Foreground of the text, used only if [attributes] are not specified
   *
   * See also [greyForeground]
   */
  var foreground: Color = foreground

  /**
   * Attributes of the text, if set then [foreground] is ignored
   */
  var attributes: SimpleTextAttributes? = null

  var font: Font? = UIManager.getFont("Label.font")
}
