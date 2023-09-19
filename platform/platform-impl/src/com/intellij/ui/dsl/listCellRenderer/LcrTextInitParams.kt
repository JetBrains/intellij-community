// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer

import com.intellij.ui.SimpleTextAttributes
import org.jetbrains.annotations.ApiStatus
import java.awt.Color

@ApiStatus.Experimental
interface LcrTextInitParams : LcrInitParams {

  /**
   * Foreground of the text, used only if [attributes] are not specified
   *
   * See also [LcrRow.greyForeground]
   */
  var foreground: Color

  /**
   * Attributes of the text, if set then [foreground] is ignored
   */
  var attributes: SimpleTextAttributes?
}
