// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.DefaultActionGroup

@Suppress("ComponentNotRegistered")
open class MoreActionGroup : DefaultActionGroup() {

  init {
    isPopup = true
    templatePresentation.icon = AllIcons.Actions.More
    templatePresentation.putClientProperty(ActionButton.HIDE_DROPDOWN_ICON, true)
  }

  override fun isDumbAware() = true

  override fun hideIfNoVisibleChildren() = true
}