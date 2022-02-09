// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal

import com.intellij.ide.util.BasePropertyService
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.ui.GotItTooltip

internal class ResetGotItTooltips : AnAction(), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    (PropertiesComponent.getInstance() as? BasePropertyService)?.removeIf {
      it.startsWith(GotItTooltip.PROPERTY_PREFIX)
    }
  }
}