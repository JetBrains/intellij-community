// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.stickyLines.configurable

import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.options.CompositeConfigurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider
import javax.swing.JComponent

internal class StickyLinesConfigurable : CompositeConfigurable<StickyLinesProviderConfigurable>(), SearchableConfigurable {

  private var panel: DialogPanel? = null

  override fun getId(): String {
    return "editor.stickyLines"
  }

  override fun getDisplayName(): String {
    return ApplicationBundle.message("configurable.sticky.lines")
  }

  override fun reset() {
    panel?.reset()
  }

  override fun isModified(): Boolean {
    return panel?.isModified() ?: false
  }

  override fun apply() {
    this.panel?.apply()
  }

  override fun createComponent(): JComponent {
    var panel = this.panel
    if (panel == null) {
      panel = StickyLinesConfigurableUI(createConfigurables()).panel
      this.panel = panel
    }
    return panel
  }

  override fun createConfigurables(): List<StickyLinesProviderConfigurable> {
    val configurables = mutableListOf<StickyLinesProviderConfigurable>()
    for (provider in BreadcrumbsProvider.EP_NAME.extensionList) {
      for (language in provider.languages) {
        configurables.add(StickyLinesProviderConfigurable(provider, language))
      }
    }
    return configurables
  }
}
