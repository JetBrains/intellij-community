// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.cloneDialog

import com.intellij.util.ui.JBValue
import org.jetbrains.annotations.ApiStatus

/**
 * Contains a lot of UI related constants for clone dialog that can be helpful for external implementations.
 */
@ApiStatus.Internal
object VcsCloneDialogUiSpec {
  object ExtensionsList {
    val iconSize: JBValue.UIInteger = JBValue.UIInteger("VcsCloneDialog.iconSize", 22)
    const val iconTitleGap: Int = 6
    const val topBottomInsets: Int = 8
    const val leftRightInsets: Int = 10
  }

  object Components {
    const val innerHorizontalGap: Int = 10
    const val avatarSize: Int = 24
    const val popupMenuAvatarSize: Int = 40
  }
}
