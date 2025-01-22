// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow.xNext.toolbar.actions.toolbar

import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.InternalUICustomization
import com.intellij.toolWindow.xNext.toolbar.actions.XNextToolbarGroup
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.CurrentTheme.Toolbar.mainToolbarButtonInsets
import com.intellij.util.ui.launchOnShow
import kotlinx.coroutines.awaitCancellation

internal class XNextActionToolbar : ActionToolbarImpl ("XNextStatusBar", XNextToolbarGroup(), true){
  init {
    setCustomButtonLook(XNextToolWindowButtonLook())
    setMinimumButtonSize { ActionToolbar.experimentalToolbarMinimumButtonSize() }
    setActionButtonBorder(JBUI.Borders.empty(mainToolbarButtonInsets()))

    launchOnShow("XNextActionToolbar") {
      try {
        ApplicationManager.getApplication().messageBus.connect(this).subscribe(LafManagerListener.TOPIC, LafManagerListener {
          updateBackground()
        })
        awaitCancellation()
      } finally {
      }
    }
    updateBackground()
  }

  private fun updateBackground() {
    background = InternalUICustomization.getInstance().getCustomMainBackgroundColor()
  }
}
