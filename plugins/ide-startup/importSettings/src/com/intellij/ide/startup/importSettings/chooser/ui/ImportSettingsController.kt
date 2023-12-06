// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.ui

import com.intellij.ide.startup.importSettings.data.ActionsDataProvider
import com.intellij.ide.startup.importSettings.data.DialogImportData
import com.intellij.ide.startup.importSettings.data.SettingsContributor
import com.jetbrains.rd.util.lifetime.Lifetime
import org.jetbrains.annotations.Nls
import javax.swing.JButton

interface ImportSettingsController {
  fun goToSettingsPage(provider: ActionsDataProvider<*>, product: SettingsContributor)
  fun goToProductChooserPage()
  fun goToImportPage(importFromProduct: DialogImportData)
  fun createButton(name: @Nls String, handler: () -> Unit): JButton
  fun createDefaultButton(name: @Nls String, handler: () -> Unit): JButton

  fun skipImport()

  val lifetime: Lifetime
}