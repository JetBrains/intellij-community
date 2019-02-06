package com.jetbrains.changeReminder.repository

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.vcs.log.VcsLogIndexService
import com.jetbrains.changeReminder.plugin.UserSettings

class LogIndexService : VcsLogIndexService {
  override fun requiresPathsForwardIndex() = Registry.`is`("git.change.reminder.enable") &&
                                             ServiceManager.getService(UserSettings::class.java).isPluginEnabled
}