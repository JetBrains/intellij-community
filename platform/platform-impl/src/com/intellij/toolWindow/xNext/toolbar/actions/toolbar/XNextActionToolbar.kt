// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow.xNext.toolbar.actions.toolbar

import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.application.impl.InternalUICustomization
import com.intellij.toolWindow.xNext.toolbar.actions.XNextToolbarGroup
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.CurrentTheme.Toolbar.mainToolbarButtonInsets

internal class XNextActionToolbar : ActionToolbarImpl ("XNextStatusBar", XNextToolbarGroup(), true){
  init {
    setCustomButtonLook(XNextToolWindowButtonLook())
    setMinimumButtonSize { ActionToolbar.experimentalToolbarMinimumButtonSize() }
    setActionButtonBorder(JBUI.Borders.empty(mainToolbarButtonInsets()))

    InternalUICustomization.getInstance()?.installBackgroundUpdater(this)
  }
}
