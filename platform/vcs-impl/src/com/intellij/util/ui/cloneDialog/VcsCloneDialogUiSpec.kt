// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.cloneDialog

import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBValue
import com.intellij.util.ui.UIUtil

/**
 * Contains a lot of UI specific constants for clone dialog that can be helpful for external implementations.
 */
object VcsCloneDialogUiSpec {
  object Dialog {
    val mainComponentParentInsets = UIUtil.PANEL_REGULAR_INSETS.let {
      // use empty right inset to align the scroll bar to the edge of panel
      JBInsets(it.top, it.left, it.bottom, 0)
    }
  }

  object ExtensionsList {
    val iconSize = JBValue.UIInteger("VcsCloneDialog.iconSize", 22)
    const val iconTitleGap = 6
    val insets = JBUI.insets(8, 10)
  }

  object Components {
    // insets for component without full-height scrollbar
    val rightInsets = JBUI.insetsRight((UIUtil.PANEL_REGULAR_INSETS.right))
  }
}
