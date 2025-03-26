// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.commit.modal

import com.intellij.ide.util.runOnceForApp
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vcs.VcsApplicationSettings

class GitModalCommitToggler : ProjectActivity {
  override suspend fun execute(project: Project) {
    runOnceForApp("git.modal.commit.toggle") {
      if (!VcsApplicationSettings.getInstance().COMMIT_FROM_LOCAL_CHANGES) {
        AdvancedSettings.setBoolean(GitModalCommitModeProvider.SETTING_ID, true)
      }
    }
  }
}