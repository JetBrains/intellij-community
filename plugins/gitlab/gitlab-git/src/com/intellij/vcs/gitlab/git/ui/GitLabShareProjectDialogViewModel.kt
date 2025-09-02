// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.gitlab.git.ui

import com.intellij.collaboration.api.HttpStatusErrorException
import com.intellij.collaboration.async.combineState
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.async.withInitial
import com.intellij.collaboration.ui.util.ValidationBinding
import com.intellij.collaboration.ui.util.validationBinding
import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.fold
import com.intellij.collaboration.util.getOrNull
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.util.coroutines.childScope
import git4idea.remote.hosting.gitRemotesStateIn
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.GitLabServersManager
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabApiManager
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.api.dto.WithGitLabNamespace
import org.jetbrains.plugins.gitlab.api.request.findProject
import org.jetbrains.plugins.gitlab.api.request.getMemberNamespacesForShare
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import org.jetbrains.plugins.gitlab.util.GitLabProjectPath
import java.util.*
import java.util.regex.Pattern

internal class GitLabShareProjectDialogViewModel(
  project: Project,
  parentCs: CoroutineScope,
  initialProjectName: @NlsSafe String,
) {
  companion object {
    private val GITLAB_REPO_PATTERN = Pattern.compile("[a-zA-Z0-9_.-]+")

    private val LOG = logger<GitLabShareProjectDialogViewModel>()
  }

  private val cs = parentCs.childScope(javaClass.name, Dispatchers.Default)

  private val accountManager: GitLabAccountManager = service<GitLabAccountManager>()
  val accounts: StateFlow<Set<GitLabAccount>> = accountManager.accountsState

  private val existingRemotes = gitRemotesStateIn(project, cs, started = SharingStarted.Eagerly)
    .mapState { remotes -> remotes.map { it.remote.name }.toSet() }

  //region: underlying properties
  private val _account: MutableStateFlow<GitLabAccount?> = MutableStateFlow(accounts.value.firstOrNull())
  private val _namespace: MutableStateFlow<@NlsSafe WithGitLabNamespace?> = MutableStateFlow<@NlsSafe WithGitLabNamespace?>(null)

  private val _repositoryName: MutableStateFlow<@NlsSafe String> = MutableStateFlow(initialProjectName)
  private val _remoteName: MutableStateFlow<@NlsSafe String> = MutableStateFlow(
    if ("origin" !in existingRemotes.value) "origin" else "gitlab"
  )

  val isPrivate: MutableStateFlow<Boolean> = MutableStateFlow(true)
  val description: MutableStateFlow<@NlsSafe String> = MutableStateFlow<@NlsSafe String>("")
  //endregion

  // re-emitted for every change in credentials
  @OptIn(ExperimentalCoroutinesApi::class)
  private val api: StateFlow<GitLabApi?> = _account.transformLatest { account ->
    if (account == null) {
      emit(null)
      return@transformLatest
    }

    coroutineScope {
      emitAll(serviceAsync<GitLabAccountManager>().getCredentialsState(this, account).map { token ->
        if (token == null) null
        else serviceAsync<GitLabApiManager>().getClient(account.server, token)
      })
    }
  }.stateIn(cs, SharingStarted.Eagerly, null)

  //region: required view data
  private val namespacesCache = Collections.synchronizedMap(mutableMapOf<String, List<WithGitLabNamespace>>())
  private val namespacesReloadRequests = MutableSharedFlow<Unit>(replay = 1)

  @OptIn(ExperimentalCoroutinesApi::class)
  val namespaces: StateFlow<ComputedResult<List<WithGitLabNamespace>>> = combine(_account, api, namespacesReloadRequests.withInitial(Unit)) { account, api, _ -> account to api }
    .transformLatest { (account, api) ->
      if (account == null) return@transformLatest //don't do anything

      // cache hit
      if (namespacesCache.containsKey(account.id)) {
        emit(ComputedResult.success(namespacesCache[account.id]!!))
        return@transformLatest
      }

      // no valid token!
      if (api == null) {
        emit(ComputedResult.failure(NoTokenAvailableException(account)))
        return@transformLatest
      }

      // cache miss -> start loading
      emit(ComputedResult.loading())

      runCatching {
        val glMetadata = serviceAsync<GitLabServersManager>().getMetadata(api)
        api.graphQL.getMemberNamespacesForShare(glMetadata).toList().flatten()
      }
        .onFailure { error ->
          LOG.info("Cannot load namespaces for account $account:", error)
          emit(ComputedResult.failure(error))
        }
        .onSuccess { namespaces ->
          namespacesCache.put(account.id, namespaces)
          emit(ComputedResult.success(namespaces))
        }
    }
    .stateIn(cs, SharingStarted.Eagerly, ComputedResult.loading())

  fun reloadNamespaces() {
    cs.launchNow {
      if (namespacesCache.remove(_account.value!!.id) != null) {
        namespacesReloadRequests.emit(Unit)
      }
    }
  }

  val reloginRequired: StateFlow<Boolean> = namespaces.mapState {
    it.fold(
      onInProgress = { false },
      onSuccess = { false },
      onFailure = { (it is HttpStatusErrorException && it.statusCode == 401) || it is NoTokenAvailableException }
    )
  }

  val hasValidAccount: StateFlow<Boolean> = _account.combineState(reloginRequired) { account, reloginRequired ->
    account != null && !reloginRequired
  }
  //endregion

  //region: validation util
  @OptIn(FlowPreview::class)
  private val isExistingRepository: StateFlow<Boolean> =
    combine(_repositoryName.debounce(500), _namespace, _account, api) { name, namespace, account, api ->
      if (account == null || api == null || namespace == null) return@combine false

      LOG.info("Checking for existing repositories at coordinates: ${account.server}/${namespace.fullPath}/$name")
      api.graphQL.findProject(GitLabProjectCoordinates(account.server, GitLabProjectPath(namespace.fullPath, name))).body() != null
    }.stateIn(cs, SharingStarted.Eagerly, false)

  private val accountValidationError: StateFlow<@NlsContexts.DialogMessage String?> =
    _account.combineState(namespaces) { account, namespaces ->
      val error = namespaces.result?.exceptionOrNull()
      (if (error != null && account != null && error is HttpStatusErrorException && error.statusCode == 401)
        GitLabBundle.message("share.validation.invalidToken", account.name)
      else null)
      ?: if (account == null) GitLabBundle.message("share.validation.noAccount") else null
    }

  private val namespaceValidationError: StateFlow<@NlsContexts.DialogMessage String?> =
    _namespace
      .combineState(namespaces) { namespace, namespaces -> namespace to namespaces }
      .combineState(_account) { (namespace, namespaces), account ->
        val error = namespaces.result?.exceptionOrNull()
        (if (error != null && account != null && (error !is HttpStatusErrorException || error.statusCode != 401)) // auth errors covered by account validation
          GitLabBundle.message("share.validation.cannotLoadNamespaces", account.name)
        else null)
        ?: (if (namespace == null) GitLabBundle.message("share.validation.noNamespace") else null)
      }

  private val repositoryNameValidationError: StateFlow<@NlsContexts.DialogMessage String?> =
    combine(_repositoryName, isExistingRepository) { repositoryName, isExistingRepository ->
      (if (repositoryName.isBlank()) GitLabBundle.message("share.validation.noRepoName") else null)
      ?: (if (!GITLAB_REPO_PATTERN.matcher(repositoryName).matches()) GitLabBundle.message("share.validation.invalidRepoName") else null)
      ?: (if (isExistingRepository) GitLabBundle.message("share.validation.repoAlreadyExists") else null)
    }.stateIn(cs, SharingStarted.Eagerly, null)

  private val remoteNameValidationError: StateFlow<@NlsContexts.DialogMessage String?> =
    combine(_remoteName, existingRemotes) { remoteName, existingRemotes ->
      (if (remoteName.isBlank()) GitLabBundle.message("share.validation.noRemoteName") else null)
      ?: (if (existingRemotes.contains(remoteName)) GitLabBundle.message("share.validation.remoteAlreadyExists") else null)
    }.stateIn(cs, SharingStarted.Eagerly, null)
  //endregion

  //region: validated bindable properties
  val account: ValidationBinding<GitLabAccount?> = _account.validationBinding(accountValidationError)
  val namespace: ValidationBinding<@NlsSafe WithGitLabNamespace?> = _namespace.validationBinding(namespaceValidationError)

  val repositoryName: ValidationBinding<@NlsSafe String> = _repositoryName.validationBinding(repositoryNameValidationError)
  val remoteName: ValidationBinding<@NlsSafe String> = _remoteName.validationBinding(remoteNameValidationError)
  //endregion

  init {
    cs.launchNow {
      namespaces.collect { namespacesResult ->
        val namespaces = namespacesResult.getOrNull() ?: return@collect
        if (_namespace.value == null || _namespace.value !in namespaces) {
          _namespace.value = namespaces.firstOrNull()
        }
      }
    }

    cs.launchNow {
      accounts.collect { accounts ->
        if (_account.value == null || _account.value !in accounts) {
          _account.value = accounts.firstOrNull()
        }
      }
    }
  }

  val state
    get() = GitLabShareProjectDialogResult(
      api.value!!,
      namespace.value!!,
      repositoryName.value,
      remoteName.value,
      isPrivate.value,
      description.value,
    )

  fun updateAccount(account: GitLabAccount, token: String) {
    cs.launchNow {
      accountManager.updateAccount(account, token)
    }
  }
}

internal data class GitLabShareProjectDialogResult(
  val api: GitLabApi,
  val namespace: WithGitLabNamespace,

  val repositoryName: @NlsSafe String,
  val remoteName: @NlsSafe String,

  val isPrivate: Boolean,
  val description: @NlsSafe String,
)

private class NoTokenAvailableException(account: GitLabAccount)
  : RuntimeException("No token for account ${account.name}")
