// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.EventDispatcher
import git4idea.remote.hosting.knownRepositories
import kotlinx.serialization.Serializable
import org.jetbrains.plugins.gitlab.GitLabProjectsManager
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping
import java.util.*

@Service(Service.Level.PROJECT)
@State(name = "GitLabMergeRequestsSettings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)], reportStatistic = false)
class GitLabMergeRequestsPreferences(private val project: Project)
  : SerializablePersistentStateComponent<GitLabMergeRequestsPreferences.SettingsState>(SettingsState()) {

  private val listeners = EventDispatcher.create(Listener::class.java)

  @Serializable
  data class SettingsState(
    val selectedUrlAndAccountId: Pair<String, String>? = null,
    val showEventsInTimeline: Boolean = true,
    val highlightDiffLinesInEditor: Boolean = true,
    val usedAsDraftSubmitActionLast: Boolean = true,
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
      updateStateAndEmit {
        val saved = value?.let { (repo, account) -> repo.remote.url to account.id }
        it.copy(selectedUrlAndAccountId = saved)
      }
    }

  var showEventsInTimeline: Boolean
    get() = state.showEventsInTimeline
    set(value) {
      updateStateAndEmit {
        it.copy(showEventsInTimeline = value)
      }
    }

  var highlightDiffLinesInEditor: Boolean
    get() = state.highlightDiffLinesInEditor
    set(value) {
      updateStateAndEmit {
        it.copy(highlightDiffLinesInEditor = value)
      }
    }

  var usedAsDraftSubmitActionLast: Boolean
    get() = state.usedAsDraftSubmitActionLast
    set(value) {
      updateStateAndEmit {
        it.copy(usedAsDraftSubmitActionLast = value)
      }
    }

  private inline fun updateStateAndEmit(updateFunction: (currentState: SettingsState) -> SettingsState) {
    val state = super.updateState(updateFunction)
    listeners.multicaster.onSettingsChange(state)
  }

  fun addListener(disposable: Disposable, listener: Listener) = listeners.addListener(listener, disposable)

  fun interface Listener : EventListener {
    fun onSettingsChange(settings: SettingsState)
  }
}