// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.openapi.components.service
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.function.Consumer
import javax.swing.JComponent

fun getPendingUpdates(): Collection<PluginUiModel>? = UpdateSettingsEntryPointActionProvider.getPendingUpdates()

fun installUpdates(updates: Collection<PluginUiModel>, component: JComponent?, customRestarter: Consumer<Boolean>?) {
  service<CoreUiCoroutineScopeHolder>().coroutineScope.launch(Dispatchers.IO) {
    PluginUpdateHandler.getInstance().installUpdates(updates, component, null, customRestarter)
  }
}