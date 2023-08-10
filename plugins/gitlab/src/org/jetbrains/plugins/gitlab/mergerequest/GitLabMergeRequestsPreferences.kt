// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import git4idea.remote.hosting.knownRepositories
import kotlinx.serialization.Serializable
import org.jetbrains.plugins.gitlab.GitLabProjectsManager
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping

@Service(Service.Level.PROJECT)
@State(name = "GitLabMergeRequestsSettings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)], reportStatistic = false)
class GitLabMergeRequestsPreferences(private val project: Project)
  : SerializablePersistentStateComponent<GitLabMergeRequestsPreferences.SettingsState>(SettingsState()) {

  @Serializable
  data class SettingsState(
    val selectedUrlAndAccountId: Pair<String, String>? = null,
    val showEventsInTimeline: Boolean = true
  )

  var selectedRepoAndAccount: Pair<GitLabProjectMapping, GitLabAccount>?
    get() {
      val (url, accountId) = state.selectedUrlAndAccountId ?: return null
      val repo = project.service<GitLabProjectsManager>().knownRepositories.find {
        it.remote.url == url
      } ?: return null
      val account = service<GitLabAccountManager>().accountsState.value.find {
        it.id == accountId
      } ?: return null
      return repo to account
    }
    set(value) {
      updateState {
        val saved = value?.let { (repo, account) -> repo.remote.url to account.id }
        SettingsState(saved)
      }
    }

  var showEventsInTimeline: Boolean
    get() = state.showEventsInTimeline
    set(value) {
      updateState {
        it.copy(showEventsInTimeline = value)
      }
    }
}