// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.snippets

import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.snippets.PathHandlingMode
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gitlab.GitLabProjectsManager
import org.jetbrains.plugins.gitlab.api.GitLabApiManager
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabProjectDefaultAccountHolder
import java.util.*
import kotlin.jvm.optionals.getOrNull

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
  _data: GitLabCreateSnippetViewModelData,
) {
  /** Flow of GitLab accounts taken from [GitLabAccountManager]. */
  val glAccounts: StateFlow<Set<GitLabAccount>> = glAccountManager.accountsState

  /**
   * Flow of the currently chosen account to use.
   * By default, this will be the first account found by [GitLabAccountManager].
   */
  val glAccount: MutableStateFlow<GitLabAccount?> = run {
    val server = project.service<GitLabProjectsManager>().knownRepositoriesState.value.firstOrNull()?.repository?.serverPath
    val account = project.service<GitLabProjectDefaultAccountHolder>().account
                  ?: glAccounts.value.find { it.server == server }
                  ?: glAccounts.value.firstOrNull()
    MutableStateFlow(account)
  }

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
  val glRepositories: StateFlow<List<GitLabProjectCoordinates>> = channelFlow {
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
  }.stateIn(cs, SharingStarted.Lazily, listOf())

  /**
   * The project the snippet will be posted under.
   * Can be `null` or `Optional.empty`, but both mean the project coordinates are left unassigned.
   */
  val onProject: MutableStateFlow<Optional<GitLabProjectCoordinates>?> = MutableStateFlow(Optional.empty())

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
  val data: MutableStateFlow<GitLabCreateSnippetViewModelData> = MutableStateFlow(_data)

  init {
    cs.launch {
      val projectsManager = project.serviceAsync<GitLabProjectsManager>()
      glRepositories.collectLatest { glProjects ->
        onProject.update {
          if (it?.isPresent == true) {
            return@update it
          }

          val knownRepositories = projectsManager.knownRepositoriesState.value
          val currentRepository = knownRepositories.find { glProjects.contains(it.repository) }
                                  ?: return@update Optional.empty()
          Optional.of(currentRepository.repository)
        }
      }
    }
  }

  /**
   * Converts the values in this view model to a final immutable result.
   */
  suspend fun toResult(): GitLabCreateSnippetResult? {
    val (account, _) = glAccountAndCredentials.firstOrNull() ?: return null
    return GitLabCreateSnippetResult(
      account,
      onProject.value?.getOrNull(),
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
  val onProject: GitLabProjectCoordinates?,
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

  val pathHandlingMode: PathHandlingMode
)
