// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl

import com.intellij.icons.AllIcons
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.DefaultActionGroup

open class MoreActionGroup @JvmOverloads constructor(
  horizontal: Boolean = true
) : DefaultActionGroup({ ActionsBundle.groupText("MoreActionGroup") }, true) {

  init {
    templatePresentation.icon = if (horizontal) AllIcons.Actions.More else AllIcons.Actions.MoreHorizontal
    templatePresentation.putClientProperty(ActionButton.HIDE_DROPDOWN_ICON, true)
    templatePresentation.isHideGroupIfEmpty = true
  }
}