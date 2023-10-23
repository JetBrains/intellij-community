// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.popup.compose

import com.intellij.ui.speedSearch.SpeedSearch
import com.intellij.ui.speedSearch.SpeedSearchSupply
import git4idea.GitBranch
import git4idea.repo.GitRepository
import git4idea.ui.branch.tree.localBranchesOrCurrent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.job
import java.awt.event.KeyEvent
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener

@OptIn(FlowPreview::class)
internal class GitBranchesComposeVm(
  private val coroutineScope: CoroutineScope,
  repository: GitRepository
) {
  val text = MutableStateFlow("")

  private val speedSearchListener = object : PropertyChangeListener {
    override fun propertyChange(evt: PropertyChangeEvent?) {
      if (evt?.propertyName != SpeedSearchSupply.ENTERED_PREFIX_PROPERTY_NAME) {
        return
      }
      text.value = evt.newValue as String
    }
  }

  private val speedSearch = SpeedSearch(true).apply {
    setEnabled(true)
    addChangeListener(speedSearchListener)
  }

  private val allLocalBranches = repository.localBranchesOrCurrent
  private val allRemoteBranches = repository.branches.remoteBranches

  val localBranches: StateFlow<List<GitBranch>> = createFilteredBranchesFlow(allLocalBranches)

  val remoteBranches: StateFlow<List<GitBranch>> = createFilteredBranchesFlow(allRemoteBranches)

  val preferredBranch: StateFlow<GitBranch?> = localBranches.combine(remoteBranches) { localBranches, remoteBranches ->
    selectPreferredBranch(localBranches, remoteBranches)
  }.stateIn(coroutineScope, SharingStarted.Eagerly, selectPreferredBranch(allLocalBranches, allRemoteBranches))

  init {
    coroutineScope.coroutineContext.job.invokeOnCompletion {
      speedSearch.removeChangeListener(speedSearchListener)
    }
  }

  private fun selectPreferredBranch(localBranches: Collection<GitBranch>, remoteBranches: Collection<GitBranch>): GitBranch? {
    // TODO: introduce better logic
    val preferredLocal = localBranches.find { it.name.contains("master") } ?: localBranches.firstOrNull()
    val preferredRemote = remoteBranches.firstOrNull()

    return preferredLocal ?: preferredRemote
  }

  private fun createFilteredBranchesFlow(branches: Collection<GitBranch>): StateFlow<List<GitBranch>> {
    // TODO: sort properly
    return text.debounce(TEXT_DEBOUNCE).map {
      branches.filter { speedSearch.shouldBeShowing(it.name) }.toList()
    }.stateIn(coroutineScope, SharingStarted.Eagerly, branches.toList())
  }

  fun handleKeyBySpeedSearch(awtEvent: KeyEvent): Boolean {
    speedSearch.processKeyEvent(awtEvent)
    return awtEvent.isConsumed
  }

  fun createBranchVm(viewScope: CoroutineScope, branch: GitBranch): GitBranchComposeVm {
    return GitBranchComposeVm(viewScope, branch, text, speedSearch)
  }

  companion object {
    private const val TEXT_DEBOUNCE = 100L
  }
}