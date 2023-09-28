package com.intellij.ide.startup.importSettings

import com.intellij.openapi.application.EDT
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.platform.ide.bootstrap.IdeStartupWizard
import com.intellij.ui.components.dialog
import com.intellij.ui.dsl.builder.panel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class IdeStartupWizardImpl : IdeStartupWizard {
  init {
    if (!System.getProperty("intellij.startup.wizard", "false").toBoolean()) {
      throw ExtensionNotApplicableException.create()
    }
  }

  @Suppress("HardCodedStringLiteral") // temporary
  override suspend fun run() {
    withContext(Dispatchers.EDT) {
      val panel = panel {
        row("Import test panel") {  }
      }
      val dialog = dialog("Import Test", panel)
      dialog.show()
    }
  }
}