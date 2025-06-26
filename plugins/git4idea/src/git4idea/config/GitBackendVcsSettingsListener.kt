// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.config

import com.intellij.dvcs.branch.DvcsBranchManager
import com.intellij.dvcs.branch.GroupingKey
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.Project
import git4idea.remoteApi.GitRepositoryFrontendSynchronizer

internal class GitBackendVcsSettingsListener(private val project: Project) : GitVcsSettings.GitVcsSettingsListener {
  override fun showTagsChanged(value: Boolean) {
    if (!value) {
      BackgroundTaskUtil.syncPublisher(project, GitRepositoryFrontendSynchronizer.TOPIC).tagsHidden()
    }
    project.getMessageBus().syncPublisher(DvcsBranchManager.DVCS_BRANCH_SETTINGS_CHANGED).showTagsSettingsChanged(value)
  }

  override fun pathToGitChanged() {
    GitExecutableDetector.fireExecutableChanged()
  }

  override fun branchGroupingSettingsChanged(key: GroupingKey, state: Boolean) {
    project.getMessageBus().syncPublisher(DvcsBranchManager.DVCS_BRANCH_SETTINGS_CHANGED).branchGroupingSettingsChanged(key, state)
  }
}