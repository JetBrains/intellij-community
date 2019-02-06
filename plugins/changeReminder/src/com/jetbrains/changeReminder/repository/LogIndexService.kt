// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.changeReminder.repository

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.vcs.log.VcsLogIndexService
import com.jetbrains.changeReminder.plugin.UserSettings

class LogIndexService : VcsLogIndexService {
  override fun requiresPathsForwardIndex() = Registry.`is`("git.change.reminder.enable") &&
                                             ServiceManager.getService(UserSettings::class.java).isPluginEnabled
}