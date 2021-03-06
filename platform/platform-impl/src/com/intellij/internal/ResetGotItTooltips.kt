// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal

import com.intellij.ide.util.PropertiesComponent
import com.intellij.ide.util.PropertiesComponentImpl
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.ui.GotItTooltip
import java.util.function.Consumer
import java.util.function.Predicate

class ResetGotItTooltips : AnAction(), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    val propertiesComponent = PropertiesComponent.getInstance()
    if (propertiesComponent is PropertiesComponentImpl) {
      propertiesComponent.keys.stream().
        filter(Predicate{ it.startsWith(GotItTooltip.PROPERTY_PREFIX)}).
        forEach(Consumer { propertiesComponent.setValue(it, null) })
    }
  }
}