// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tree

import com.intellij.openapi.options.advanced.AdvancedSettingsChangeListener
import com.intellij.ui.SmartExpander

internal class RecursiveExpandSettingListener : AdvancedSettingsChangeListener {
  override fun advancedSettingChanged(id: String, oldValue: Any, newValue: Any) {
    if (id == "ide.tree.collapse.recursively") {
      SmartExpander.setRecursiveCollapseEnabled(newValue as Boolean)
    }
  }
}
