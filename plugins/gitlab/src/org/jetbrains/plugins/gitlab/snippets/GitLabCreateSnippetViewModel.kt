// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.snippets

import com.intellij.collaboration.async.modelFlow
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gitlab.api.GitLabApiManager
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.util.GitLabBundle.message

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
  glAccountManager: GitLabAccountManager,
  glApiManager: GitLabApiManager,
  val availablePathModes: Set<PathHandlingMode>,
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
          glApiManager.getClient(token).graphQL
            .getSnippetAllowedProjects(account.server)
            .shareIn(flowCs, SharingStarted.Lazily, 1) // Let this live for as long as the repositories flow lives
        }.collectLatest { send(it) }
      }
  }.modelFlow(cs, LOG)

  /**
   * Mutable flow of the current static view model data.
   */
  val data: MutableStateFlow<GitLabCreateSnippetViewModelData> = MutableStateFlow(data)

  /**
   * Converts the values in this view model to a final immutable result.
   */
  suspend fun toResult(): GitLabCreateSnippetResult? {
    val (account, _) = glAccountAndCredentials.firstOrNull() ?: return null
    return GitLabCreateSnippetResult(
      account,
      data.value
    )
  }
}

/**
 * The final, immutable result of a create-snippet action dialog.
 */
internal data class GitLabCreateSnippetResult(
  val account: GitLabAccount,
  val data: GitLabCreateSnippetViewModelData
)

/**
 * Data that can be stored and changed representing the inputs from the create-snippet dialog.
 */
internal data class GitLabCreateSnippetViewModelData(
  val title: @Nls String,
  val description: @Nls String,

  val isPrivate: Boolean,
  val isCopyUrl: Boolean,
  val isOpenInBrowser: Boolean,

  val onProject: GitLabProjectCoordinates?,
  val pathHandlingMode: PathHandlingMode)
