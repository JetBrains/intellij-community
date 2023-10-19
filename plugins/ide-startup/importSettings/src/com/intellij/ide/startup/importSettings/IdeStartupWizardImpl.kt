// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings

import com.intellij.ide.startup.importSettings.chooser.productChooser.ProductChooserDialog
import com.intellij.ide.startup.importSettings.chooser.ui.MultiplePageDialog
import com.intellij.ide.startup.importSettings.data.SettingsService
import com.intellij.openapi.application.EDT
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.platform.ide.bootstrap.IdeStartupWizard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

private class IdeStartupWizardImpl : IdeStartupWizard {
  init {
    if (!System.getProperty("intellij.startup.wizard", "false").toBoolean()) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun run() {
    coroutineScope {
      // Fire-and-forget call to warm up the external settings transfer
      async { SettingsService.getInstance().getExternalService().warmUp() }

      withContext(Dispatchers.EDT) {

        MultiplePageDialog.show(ProductChooserDialog())
      }
    }
  }
}