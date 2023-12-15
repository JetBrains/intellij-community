// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.data

import com.intellij.openapi.components.service

interface WizardProvider {
  companion object {
    fun getInstance(): WizardProvider = service()
  }

  fun getWizardService(): WizardService? = null
}

interface WizardService {
  fun getKeymapService(): KeymapService

  fun getThemeService(): ThemeService

  fun getPluginService(): PluginService
}

interface ThemeService {

}

interface PluginService {

}

interface KeymapService {
  val maps: List<WizardKeymap>
  fun chosen(id: String)
}

data class WizardKeymap (
  val id: String,
  val title: String,
  val description: String,
  val keymaps: List<Shortcut>
)

data class Shortcut (
  val name: String,
  val shortcut: String
)