// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.util

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.ex.ToolbarLabelAction
import org.jetbrains.annotations.Nls

class GHToolbarLabelAction(@Nls text: String) : ToolbarLabelAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  init {
    templatePresentation.text = text
  }
}