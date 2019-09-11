// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.cloneDialog

import com.intellij.util.ui.JBValue

/**
 * Contains a lot of UI related constants for clone dialog that can be helpful for external implementations.
 */
object VcsCloneDialogUiSpec {
  object ExtensionsList {
    val iconSize = JBValue.UIInteger("VcsCloneDialog.iconSize", 22)
    const val iconTitleGap = 6
    const val topBottomInsets = 8
    const val leftRightInsets = 10
  }

  object Components {
    const val innerHorizontalGap = 10
    const val avatarSize = 24
    const val popupMenuAvatarSize = 40
  }
}
