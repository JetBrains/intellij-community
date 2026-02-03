// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.openapi.fileEditor.CompositeTabIconHolderCreator
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.TabInfoIconHolder

internal class CompositeTabIconHolderCreatorImpl : CompositeTabIconHolderCreator {
  override fun createTabIconHolder(composite: EditorComposite, owner: TabInfo): TabInfoIconHolder {
    val isFeatureFlagEnabled = ExperimentalUI.isNewUI() && Registry.`is`("editor.loading.spinner.enabled", true)
    if (!isFeatureFlagEnabled) {
      return TabInfoIconHolder.default(owner)
    }
    return SpinnerTabIconHolder(composite, owner)
  }
}
