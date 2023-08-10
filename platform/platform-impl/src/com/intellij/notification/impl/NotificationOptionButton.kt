// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification.impl

import com.intellij.ui.components.JBOptionButton
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import javax.swing.Action

/**
 * @author Alexander Lobas
 */
open class NotificationOptionButton(action: Action?, options: Array<Action>?) : JBOptionButton(action, options) {
  init {
    addSeparator = false
    selectFirstItem = false
    popupBackgroundColor = UIUtil.getListBackground()
    showPopupYOffset = 0

    popupHandler = { popup ->
      val size = Dimension(popup.size)
      if (size.width < width) {
        size.width = width
        popup.size = size
      }
    }
  }
}