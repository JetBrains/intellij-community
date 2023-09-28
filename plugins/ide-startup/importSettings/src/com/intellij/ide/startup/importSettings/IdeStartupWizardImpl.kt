package com.intellij.ide.startup.importSettings

import com.intellij.ide.startup.importSettings.chooser.ImportSettingsFromDialog
import com.intellij.openapi.application.EDT
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.platform.ide.bootstrap.IdeStartupWizard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class IdeStartupWizardImpl : IdeStartupWizard {
  init {
    if (!System.getProperty("intellij.startup.wizard", "false").toBoolean()) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun run() {
    withContext(Dispatchers.EDT) {
      val dialog = ImportSettingsFromDialog().apply {
        isResizable = false
        pack()
      }
      dialog.show()
    }
  }
}