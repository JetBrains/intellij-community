// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow.xNext.toolbar

import com.intellij.openapi.application.impl.InternalUICustomization
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import java.awt.Color

internal class XNextBar : JBPanel<JBPanel<*>>() {

  override fun getBackground(): Color? {
    return InternalUICustomization.getInstance().getCustomMainBackgroundColor() ?: JBUI.CurrentTheme.StatusBar.BACKGROUND
  }
}