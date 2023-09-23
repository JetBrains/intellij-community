// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.snippets

import com.intellij.collaboration.async.modelFlow
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import git4idea.remote.hosting.knownRepositories
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gitlab.GitLabProjectsManager
import org.jetbrains.plugins.gitlab.api.GitLabApiManager
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.authentication.GitLabLoginUtil
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import javax.swing.JComponent

private val LOG = logger<GitLabCreateSnippetViewModel>()

/**
 * Representation of file contents for a single file in a snippet.
 * This could be a (multi-)selection in a file, or it could be the whole file contents.
 * Either way, the file contents should always come from the file also passed to this class.
 */
class GitLabSnippetFileContents(
  val file: VirtualFile?,
  val capturedContents: String
)

/**
 * View model for creating GitLab snippets.
 */
@ApiStatus.Experimental
@OptIn(ExperimentalCoroutinesApi::class)
internal class GitLabCreateSnippetViewModel(
  private val cs: CoroutineScope,
  val project: Project,
  private val glAccountManager: GitLabAccountManager,
  glApiManager: GitLabApiManager,
  val availablePathModes: Set<PathHandlingMode>,
  contents: Deferred<List<GitLabSnippetFileContents>>,
  data: GitLabCreateSnippetViewModelData,
) {
  /** Flow of GitLab accounts taken from [GitLabAccountManager]. */
  val glAccounts: StateFlow<Set<GitLabAccount>> = glAccountManager.accountsState
    .stateIn(cs, SharingStarted.Lazily, emptySet())

  /**
   * Flow of the currently chosen account to use.
   * By default, this will be the first account found by [GitLabAccountManager].
   */
  val glAccount: MutableStateFlow<GitLabAccount?> = MutableStateFlow(glAccounts.value.firstOrNull())

  /**
   * Flow of the current account and credentials for that account. Credentials can be null for an account.
   */
  private val glAccountAndCredentials: SharedFlow<Pair<GitLabAccount, String?>?> = glAccount
    .flatMapLatest { accountOrNull ->
      val account = accountOrNull ?: return@flatMapLatest flowOf(null)
      glAccountManager.getCredentialsState(cs, account)
        .map { Pair(account, it) }
    }
    .modelFlow(cs, LOG)

  /** Flow of [GitLabProjectCoordinates] based on the current selection of account. */
  val glRepositories: SharedFlow<List<GitLabProjectCoordinates>> = channelFlow {
    val flowCs = this
    val cache = mutableMapOf<Pair<GitLabAccount, String?>?, Flow<List<GitLabProjectCoordinates>>>()
    glAccountAndCredentials
      .collectLatest { credentials ->
        if (credentials == null) {
          return@collectLatest
        }

        cache.computeIfAbsent(credentials) { _ ->
          val (account, tokenOrNull) = credentials
          val token = tokenOrNull ?: return@computeIfAbsent flowOf(listOf())
          glApiManager.getClient(account.server, token).graphQL
            .getSnippetAllowedProjects()
            .shareIn(flowCs, SharingStarted.Lazily, 1) // Let this live for as long as the repositories flow lives
        }.collectLatest { send(it) }
      }
  }.modelFlow(cs, LOG)

  /**
   * Lists the collected contents for the snippet that are completely empty.
   */
  val emptyContents: Deferred<List<GitLabSnippetFileContents>> =
    cs.async { contents.await().filter { it.capturedContents.isEmpty() } }

  /**
   * Collects contents to check whether any of the contents are usable for a snippet.
   */
  val nonEmptyContents: Deferred<List<GitLabSnippetFileContents>> =
    cs.async { contents.await().filter { it.capturedContents.isNotEmpty() } }

  /**
   * Mutable flow of the current static view model data.
   */
  val data: MutableStateFlow<GitLabCreateSnippetViewModelData> = MutableStateFlow(data)

  /**
   * Launches a dialog to login to a new account. Called when no account is currently present in the
   * account state and the user clicks some link to add an account. After this function is complete
   * and a new account is added, an update should be pushed to the account state from [GitLabAccountManager].
   */
  fun performNewLogin(parentComponent: JComponent) {
    cs.launch(Dispatchers.Main + ModalityState.stateForComponent(parentComponent).asContextElement()) {
      val defaultServer = project.service<GitLabProjectsManager>().knownRepositories.firstOrNull()?.repository?.serverPath
                          ?: GitLabServerPath.DEFAULT_SERVER
      val (account, token) = GitLabLoginUtil.logInViaToken(project, parentComponent, defaultServer) { server, username ->
        GitLabLoginUtil.isAccountUnique(glAccountManager.accountsState.value, server, username)
      } ?: return@launch
      glAccountManager.updateAccount(account, token)
    }
  }

  /**
   * Converts the values in this view model to a final immutable result.
   */
  suspend fun toResult(): GitLabCreateSnippetResult? {
    val (account, _) = glAccountAndCredentials.firstOrNull() ?: return null
    return GitLabCreateSnippetResult(
      account,
      nonEmptyContents.await(),
      data.value
    )
  }
}

/**
 * The final, immutable result of a create-snippet action dialog.
 */
internal data class GitLabCreateSnippetResult(
  val account: GitLabAccount,
  val nonEmptyContents: List<GitLabSnippetFileContents>,
  val data: GitLabCreateSnippetViewModelData
)

/**
 * Data that can be stored and changed representing the inputs from the create-snippet dialog.
 */
internal data class GitLabCreateSnippetViewModelData(
  val title: @NlsSafe String,
  val description: @NlsSafe String,

  val isPrivate: Boolean,
  val isCopyUrl: Boolean,
  val isOpenInBrowser: Boolean,

  val onProject: GitLabProjectCoordinates?,
  val pathHandlingMode: PathHandlingMode)
