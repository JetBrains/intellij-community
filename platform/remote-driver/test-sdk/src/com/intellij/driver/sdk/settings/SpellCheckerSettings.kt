package com.intellij.driver.sdk.settings

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.Project

fun Driver.getSpellCheckerSettingsInstance(project: Project) = utility(SpellCheckerSettings::class).getInstance(project)

@Remote(
  value = "com.intellij.spellchecker.settings.SpellCheckerSettings",
  plugin = "com.intellij/intellij.spellchecker",
)
interface SpellCheckerSettings {
  fun getInstance(project: Project): SpellCheckerSettings
  fun getDictionaryToSave(): String?
}