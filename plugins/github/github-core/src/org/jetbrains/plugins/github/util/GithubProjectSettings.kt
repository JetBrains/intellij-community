// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.util

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import git4idea.config.GitProtectedBranchProvider
import java.util.*

@Service(Service.Level.PROJECT)
@State(name = "GithubProjectSettings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)], reportStatistic = false)
class GithubProjectSettings : PersistentStateComponentWithModificationTracker<GithubProjectSettings.State> {
  private var state = State()

  class State : BaseState() {
    var branchProtectionPatterns by list<String>()
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
    return GithubProjectSettings.getInstance(project).branchProtectionPatterns
  }
}