// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import javax.swing.JComponent

fun getPendingUpdates(): Collection<PluginDownloader>? = UpdateSettingsEntryPointActionProvider.getPendingUpdates()

fun installUpdates(updates: Collection<PluginDownloader>, component: JComponent?, callback: Runnable?) {
  PluginUpdateDialog.runUpdateAll(updates, component, callback)
}