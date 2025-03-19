// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.commit.modal

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.advanced.AdvancedSettingsChangeListener
import com.intellij.vcs.commit.CommitModeManager

internal class GitModalCommitSettingsListener() : AdvancedSettingsChangeListener {
  override fun advancedSettingChanged(id: String, oldValue: Any, newValue: Any) {
    if (id == GitModalCommitModeProvider.SETTING_ID && oldValue != newValue) {
      ApplicationManager.getApplication().messageBus.syncPublisher(CommitModeManager.SETTINGS).settingsChanged()
    }
  }
}