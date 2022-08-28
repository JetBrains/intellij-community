// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remote.hosting.ui

import com.intellij.collaboration.auth.ServerAccount
import git4idea.remote.hosting.HostedGitRepositoryMapping
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface RepositoryAndAccountSelectorViewModel<M : HostedGitRepositoryMapping, A : ServerAccount> {
  val repositoriesState: StateFlow<Set<M>>
  val repoSelectionState: MutableStateFlow<M?>

  val accountsState: StateFlow<List<A>>
  val accountSelectionState: MutableStateFlow<A?>

  val missingCredentialsState: StateFlow<Boolean>

  val submitAvailableState: StateFlow<Boolean>
  fun submitSelection()
}