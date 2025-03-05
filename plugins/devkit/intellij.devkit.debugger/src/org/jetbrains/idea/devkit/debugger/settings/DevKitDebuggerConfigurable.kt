// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.debugger.settings

import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.idea.devkit.debugger.DevKitDebuggerBundle

private class DevKitDebuggerConfigurableProvider : ConfigurableProvider() {
  override fun createConfigurable() = DevKitDebuggerConfigurable()
}

private class DevKitDebuggerConfigurable : BoundSearchableConfigurable(DevKitDebuggerBundle.message("configurable.name.ide.debugger"), "", "devkit.debugger") {
  override fun createPanel(): DialogPanel {
    val settings = DevKitDebuggerSettings.getInstance()
    return panel {
      row {
        checkBox(DevKitDebuggerBundle.message("configurable.show.ide.state.checkbox"))
          .bindSelected(settings::showIdeState)
      }
    }
  }
}
