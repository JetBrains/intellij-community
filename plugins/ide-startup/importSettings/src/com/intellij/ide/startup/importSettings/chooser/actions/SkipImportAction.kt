// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

class SkipImportAction : DumbAwareAction(), LinkAction {
  init {
    templatePresentation.text = "Skip Import"
    templatePresentation.icon = null
  }

  override fun displayTextInToolbar(): Boolean {
    return true
  }

  override fun actionPerformed(e: AnActionEvent) {

  }

}

interface LinkAction