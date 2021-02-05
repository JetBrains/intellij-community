// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.util

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import git4idea.config.GitProtectedBranchProvider
import org.jetbrains.plugins.github.api.GHRepositoryPath
import java.util.*

@State(name = "GithubProjectSettings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)], reportStatistic = false)
class GithubProjectSettings : PersistentStateComponentWithModificationTracker<GithubProjectSettings.State> {
  private var state = State()

  class State : BaseState() {
    var CREATE_PULL_REQUEST_DEFAULT_BRANCH by string(null)
    var CREATE_PULL_REQUEST_DEFAULT_REPO_USER by string(null)
    var CREATE_PULL_REQUEST_DEFAULT_REPO_NAME by string(null)

    var branchProtectionPatterns by list<String>()
  }

  var createPullRequestDefaultBranch: String?
    get() = state.CREATE_PULL_REQUEST_DEFAULT_BRANCH
    set(value) {
      state.CREATE_PULL_REQUEST_DEFAULT_BRANCH = value
    }

  var createPullRequestDefaultRepo: GHRepositoryPath?
    get() = if (state.CREATE_PULL_REQUEST_DEFAULT_REPO_USER == null || state.CREATE_PULL_REQUEST_DEFAULT_REPO_NAME == null) {
      null
    }
    else GHRepositoryPath(state.CREATE_PULL_REQUEST_DEFAULT_REPO_USER!!,
                          state.CREATE_PULL_REQUEST_DEFAULT_REPO_NAME!!)
    set(value) {
      state.CREATE_PULL_REQUEST_DEFAULT_REPO_USER = value?.owner
      state.CREATE_PULL_REQUEST_DEFAULT_REPO_NAME = value?.repository
    }

  var branchProtectionPatterns: MutableList<String>
    get() = Collections.unmodifiableList(state.branchProtectionPatterns)
    set(value) {
      state.branchProtectionPatterns = value
    }

  override fun getStateModificationCount() = state.modificationCount
  override fun getState() = state
  override fun loadState(state: State) {
    this.state = state
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): GithubProjectSettings = project.service()
  }
}

internal class GithubProtectedBranchProvider : GitProtectedBranchProvider {

  override fun doGetProtectedBranchPatterns(project: Project): List<String> {
    return project.service<GithubProjectSettings>().branchProtectionPatterns
  }
}
