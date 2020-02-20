// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.ui

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.GraziePlugin
import com.intellij.grazie.ide.ui.components.settings.GrazieSettingsPanel
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.options.ConfigurableBase

class GrazieConfigurable : ConfigurableBase<GrazieSettingsPanel, GrazieConfig>("reference.settingsdialog.project.grazie",
                                                                               GraziePlugin.name, null) {
  private lateinit var ui: GrazieSettingsPanel

  override fun getSettings(): GrazieConfig = ServiceManager.getService(GrazieConfig::class.java)

  override fun createUi(): GrazieSettingsPanel = GrazieSettingsPanel().also { ui = it }

  override fun enableSearch(option: String?) = ui.showOption(option)
}
