// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.popup.compose

import com.intellij.dvcs.branch.DvcsBranchManager
import com.intellij.dvcs.branch.DvcsBranchManager.DVCS_BRANCH_SETTINGS_CHANGED
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.MinusculeMatcher
import com.intellij.ui.speedSearch.SpeedSearch
import com.intellij.ui.speedSearch.SpeedSearchSupply
import git4idea.GitBranch
import git4idea.branch.GitBranchIncomingOutgoingManager
import git4idea.branch.GitBranchType
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitBranchManager
import git4idea.ui.branch.tree.getBranchComparator
import git4idea.ui.branch.tree.getPreferredBranch
import git4idea.ui.branch.tree.localBranchesOrCurrent
import git4idea.ui.branch.tree.matchBranches
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.job
import java.awt.event.KeyEvent
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener

@OptIn(FlowPreview::class)
internal class GitBranchesComposeVm(
  private val project: Project,
  private val coroutineScope: CoroutineScope,
  private val repository: GitRepository
) {
  private val _text = MutableStateFlow("")
  val text: StateFlow<String> = _text.asStateFlow()

  private val speedSearchListener = object : PropertyChangeListener {
    override fun propertyChange(evt: PropertyChangeEvent?) {
      if (evt?.propertyName != SpeedSearchSupply.ENTERED_PREFIX_PROPERTY_NAME) {
        return
      }
      _text.value = evt.newValue as String
    }
  }

  private val speedSearch = SpeedSearch(true).apply {
    setEnabled(true)
    addChangeListener(speedSearchListener)
  }

  private val branchManager = project.service<GitBranchManager>()

  private val localBranchesComparator: Comparator<GitBranch> =
    getBranchComparator(listOf(repository), branchManager.getFavoriteBranches(GitBranchType.LOCAL), isPrefixGrouping = { false })
  private val remoteBranchesComparator: Comparator<GitBranch> =
    getBranchComparator(listOf(repository), branchManager.getFavoriteBranches(GitBranchType.LOCAL), isPrefixGrouping = { false })

  private val allLocalBranches = repository.localBranchesOrCurrent.sortedWith(localBranchesComparator)
  private val allRemoteBranches = repository.branches.remoteBranches.sortedWith(remoteBranchesComparator)

  val localBranches: StateFlow<List<GitBranch>> = createFilteredBranchesFlow(allLocalBranches)

  val remoteBranches: StateFlow<List<GitBranch>> = createFilteredBranchesFlow(allRemoteBranches)

  val preferredBranch: StateFlow<GitBranch?> = combine(localBranches, remoteBranches, _text) { localBranches, remoteBranches, _ ->
    val matcher = speedSearch.matcher as? MinusculeMatcher
    selectPreferredBranch(matcher, localBranches, remoteBranches)
  }.stateIn(
    coroutineScope,
    initialValue = selectPreferredBranch(speedSearch.matcher as? MinusculeMatcher, allLocalBranches, allRemoteBranches),
    started = SharingStarted.Eagerly
  )

  private val favouriteChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

  private val branchManagerListener = object : DvcsBranchManager.DvcsBranchManagerListener {
    override fun branchFavoriteSettingsChanged() {
      favouriteChanged.tryEmit(Unit)
    }
  }

  init {
    project.messageBus.connect(coroutineScope).subscribe(DVCS_BRANCH_SETTINGS_CHANGED, branchManagerListener)
    coroutineScope.coroutineContext.job.invokeOnCompletion {
      speedSearch.removeChangeListener(speedSearchListener)
    }
  }

  private fun selectPreferredBranch(
    matcher: MinusculeMatcher?,
    localBranches: List<GitBranch>,
    remoteBranches: List<GitBranch>
  ): GitBranch? {
    if (matcher == null) {
      return getPreferredBranch(project, listOf(repository), localBranches)
    }
    val localBranchesMatch = matchBranches(matcher, localBranches)
    if (localBranchesMatch.topMatch != null) {
      return localBranchesMatch.topMatch
    }
    return matchBranches(matcher, remoteBranches).topMatch
  }

  private fun createFilteredBranchesFlow(branches: Collection<GitBranch>): StateFlow<List<GitBranch>> {
    // TODO: sort properly
    return _text.debounce(TEXT_DEBOUNCE).map {
      branches.filter { speedSearch.shouldBeShowing(it.name) }.toList()
    }.stateIn(coroutineScope, SharingStarted.Eagerly, branches.toList())
  }

  fun handleKeyBySpeedSearch(awtEvent: KeyEvent): Boolean {
    speedSearch.processKeyEvent(awtEvent)
    return awtEvent.isConsumed
  }

  fun updateSpeedSearchText(newText: String) {
    speedSearch.updatePattern(newText)
  }

  fun createBranchVm(viewScope: CoroutineScope, branch: GitBranch): GitBranchComposeVm {
    val isFavorite = favouriteChanged.map {
      branchManager.isFavorite(GitBranchType.of(branch), repository, branch.name)
    }.stateIn(viewScope, SharingStarted.Eagerly, branchManager.isFavorite(GitBranchType.of(branch), repository, branch.name))

    return GitBranchComposeVm(
      viewScope, repository,
      branch, _text,
      incomingOutgoingManager = GitBranchIncomingOutgoingManager.getInstance(project),
      isFavorite = isFavorite,
      toggleIsFavoriteState = {
        branchManager.setFavorite(GitBranchType.of(branch), repository, branch.name, !isFavorite.value)
      },
      isCurrent = repository.currentBranch == branch,
      speedSearch = speedSearch
    )
  }

  companion object {
    private const val TEXT_DEBOUNCE = 100L
  }
}