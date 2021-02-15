// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.ui.grammar

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.GraziePlugin
import com.intellij.grazie.ide.language.LanguageGrammarChecking
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.BaseExtensionPointName
import com.intellij.openapi.options.Configurable.WithEpDependencies
import com.intellij.openapi.options.ConfigurableBase

internal class GrazieConfigurable : ConfigurableBase<GrazieSettingsPanel, GrazieConfig>("reference.settingsdialog.project.grazie",
                                                                               GraziePlugin.name,
                                                                               "reference.settings.ide.settings.grammar"), WithEpDependencies {
  private val ui: GrazieSettingsPanel by lazy { GrazieSettingsPanel() }

  override fun getSettings() = service<GrazieConfig>()

  override fun createUi(): GrazieSettingsPanel = ui

  override fun getDependencies(): Collection<BaseExtensionPointName<*>> {
    return setOf(LanguageGrammarChecking.EP_NAME)
  }
}
