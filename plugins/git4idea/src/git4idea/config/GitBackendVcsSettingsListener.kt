// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.config

import com.intellij.dvcs.branch.DvcsBranchManager
import com.intellij.openapi.project.Project

internal class GitBackendVcsSettingsListener(private val project: Project) : GitVcsSettings.GitVcsSettingsListener {
  override fun showTagsChanged(value: Boolean) {
    project.getMessageBus().syncPublisher(DvcsBranchManager.DVCS_BRANCH_SETTINGS_CHANGED).showTagsSettingsChanged(value)
  }

  override fun pathToGitChanged() {
    GitExecutableDetector.fireExecutableChanged()
  }
}