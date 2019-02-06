package com.jetbrains.changeReminder.plugin.config

import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.util.registry.Registry

class ChangeReminderConfigurationProvider : ConfigurableProvider() {
  override fun createConfigurable() = ChangeReminderConfigurationPanel()

  override fun canCreateConfigurable() = Registry.`is`("git.change.reminder.enable")
}