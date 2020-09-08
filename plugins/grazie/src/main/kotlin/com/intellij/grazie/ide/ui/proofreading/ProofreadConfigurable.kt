package com.intellij.grazie.ide.ui.proofreading

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.ide.ui.components.dsl.msg
import com.intellij.openapi.components.service
import com.intellij.openapi.options.ConfigurableBase

class ProofreadConfigurable : ConfigurableBase<ProofreadSettingsPanel, GrazieConfig>("proofread", msg("grazie.group.name"),
                                                                                     "reference.settings.ide.settings.proofreading") {
  private val ui by lazy { ProofreadSettingsPanel() }

  override fun getSettings() = service<GrazieConfig>()
  override fun createUi() = ui
}
