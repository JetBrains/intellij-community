// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.commit.modal

import com.intellij.dvcs.commit.DvcsCommitModeProvider
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.vcs.commit.CommitMode

internal class ModalCommitModeProvider : DvcsCommitModeProvider {
  override fun getCommitMode(): CommitMode? {
    return if (AdvancedSettings.getBoolean(SETTING_ID)) CommitMode.ModalCommitMode else null
  }

  companion object {
    const val SETTING_ID = "git.non.modal.commit"
  }
}
