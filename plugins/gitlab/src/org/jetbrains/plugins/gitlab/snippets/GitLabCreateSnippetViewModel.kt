// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.snippets

import com.intellij.collaboration.async.mapMemoized
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabApiManager
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.util.GitLabBundle.message

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
@OptIn(ExperimentalCoroutinesApi::class)
class GitLabCreateSnippetViewModel(
  private val cs: CoroutineScope,
  val project: Project,
  val contents: Deferred<List<GitLabSnippetFileContents>>,
  val availablePathModes: Set<PathHandlingMode>,
  val data: CreateSnippetViewModelData,
) {
  /** Flow of GitLab accounts taken from [GitLabAccountManager]. */
  val glAccounts: StateFlow<Set<GitLabAccount>> = service<GitLabAccountManager>().accountsState
    .stateIn(cs, SharingStarted.Eagerly, emptySet())

  private val _glAccount: MutableStateFlow<GitLabAccount?> = MutableStateFlow(glAccounts.value.firstOrNull())

  /**
   * Flow of the currently chosen account to use.
   * By default, this will be the first account found by [GitLabAccountManager].
   */
  val glAccount: StateFlow<GitLabAccount?> = _glAccount.asStateFlow()

  private val glAccountAndCredentials: SharedFlow<Pair<GitLabAccount, String?>?> = glAccount
    .flatMapLatest { accountOrNull ->
      val account = accountOrNull ?: return@flatMapLatest flowOf(null)
      service<GitLabAccountManager>().getCredentialsState(cs, account)
        .map { Pair(account, it) }
    }
    .shareIn(cs, SharingStarted.Lazily, 1)

  /** Flow of [GitLabProjectCoordinates] based on the current selection of account. */
  val glRepositories: SharedFlow<List<GitLabProjectCoordinates>> = glAccountAndCredentials
    .mapMemoized { credentials ->
      val (account, tokenOrNull) = credentials ?: return@mapMemoized flowOf(listOf())
      val token = tokenOrNull ?: return@mapMemoized flowOf(listOf())
      service<GitLabApiManager>().getClient(token).graphQL
        .getSnippetAllowedProjects(account.server)
        .shareIn(cs.childScope(Dispatchers.IO), SharingStarted.Lazily, 1)
    }
    .flatMapLatest { it }
    .shareIn(cs, SharingStarted.Eagerly, 1)

  suspend fun setAccount(account: GitLabAccount) {
    _glAccount.emit(account)
  }

  fun getApi(): GitLabApi? {
    val (_, tokenOrNull) = glAccountAndCredentials.replayCache.firstOrNull() ?: return null
    val token = tokenOrNull ?: return null
    return service<GitLabApiManager>().getClient(token)
  }

  fun getServer(): GitLabServerPath? {
    val (account, _) = glAccountAndCredentials.replayCache.firstOrNull() ?: return null
    return account.server
  }
}

/**
 * Data that can be stored and changed representing the inputs from the create-snippet dialog.
 */
class CreateSnippetViewModelData(
  var title: @Nls String,
  var description: @Nls String,

  var isPrivate: Boolean,
  var isCopyUrl: Boolean,
  var isOpenInBrowser: Boolean,

  var onProject: GitLabProjectCoordinates?,
  var pathHandlingMode: PathHandlingMode)

/**
 * Ways to deal with paths for file names before creating a snippet.
 *
 * When creating a snippet from $PROJECT_ROOT/sub/readme.md and $PROJECT_ROOT/sub/a/Main.java, one can choose
 * to have all names normalized to the nearest common parent directory ($PROJECT_ROOT/sub), or the project
 * root, or just leave out directories all together.
 *
 * Example: Using RelativePaths as the setting should result in two files with names readme.md and a/Main.java
 * being a part of the snippet.
 */
enum class PathHandlingMode(@Nls val displayName: String, @Nls val tooltip: String? = null) {
  /** Uses file paths relative to the nearest common parent directory. */
  RelativePaths(message("snippet.create.path-mode.relative"), message("snippet.create.path-mode.relative.tooltip")),

  /** Does not use file paths at all, only file names are used. */
  FlattenedPaths(message("snippet.create.path-mode.none"), message("snippet.create.path-mode.none.tooltip")),

  /** Uses file paths relative to the project root. */
  ContentRootRelativePaths(message("snippet.create.path-mode.content-root-relative"),
                           message("snippet.create.path-mode.content-root-relative.tooltip")),

  /** Uses file paths relative to the project root. */
  ProjectRelativePaths(message("snippet.create.path-mode.project-relative"), message("snippet.create.path-mode.project-relative.tooltip")),
}