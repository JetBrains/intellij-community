package com.intellij.grazie.ide.ui.proofreading

import com.intellij.grazie.GrazieConfig
import com.intellij.openapi.components.service
import com.intellij.openapi.options.ConfigurableBase
import com.intellij.openapi.options.OptionsBundle
import kotlinx.coroutines.CoroutineScope

class ProofreadConfigurable : ConfigurableBase<ProofreadSettingsPanel, GrazieConfig>(
  "proofread",
  OptionsBundle.message("configurable.group.proofread.settings.display.name"),
  "reference.settings.ide.settings.proofreading"
) {
  private val ui by lazy { ProofreadSettingsPanel() }

  override fun getSettings() = service<GrazieConfig>()
  override fun createUi() = ui
}
