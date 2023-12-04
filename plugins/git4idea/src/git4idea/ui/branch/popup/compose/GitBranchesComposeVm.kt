// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.popup.compose

import androidx.compose.runtime.*
import com.intellij.dvcs.branch.DvcsBranchManager
import com.intellij.dvcs.branch.DvcsBranchManager.DVCS_BRANCH_SETTINGS_CHANGED
import com.intellij.icons.ExpUiIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.MinusculeMatcher
import com.intellij.ui.speedSearch.SpeedSearch
import com.intellij.ui.speedSearch.SpeedSearchSupply
import git4idea.GitBranch
import git4idea.GitUtil
import git4idea.branch.GitBranchIncomingOutgoingManager
import git4idea.branch.GitBranchType
import git4idea.branch.GitBrancher
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitBranchManager
import git4idea.ui.branch.GitRefDialog
import git4idea.ui.branch.createOrCheckoutNewBranch
import git4idea.ui.branch.tree.getBranchComparator
import git4idea.ui.branch.tree.getPreferredBranch
import git4idea.ui.branch.tree.localBranchesOrCurrent
import git4idea.ui.branch.tree.matchBranches
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import java.awt.event.KeyEvent
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener

@OptIn(FlowPreview::class)
internal class GitBranchesComposeVm(
  private val project: Project,
  private val coroutineScope: CoroutineScope,
  private val repository: GitRepository
) {
  val actions = listOf(
    BranchesAction(
      GitBundle.message("action.Git.CreateNewBranch.text"),
      icon = "expui/general/add.svg", iconClass = ExpUiIcons::class.java,
      action = {
        createOrCheckoutNewBranch(project, listOf(repository), GitUtil.HEAD, initialName = repository.currentBranch?.name)
      }
    ),
    BranchesAction(
      GitBundle.message("branches.checkout.tag.or.revision"),
      icon = null, iconClass = null,
      action = {
        // TODO: taken from GitCheckoutFromInputAction. Can be reused
        val dialog = GitRefDialog(project, listOf(repository),
                                  GitBundle.message("branches.checkout"),
                                  GitBundle.message("branches.enter.reference.branch.tag.name.or.commit.hash"))
        if (dialog.showAndGet()) {
          val reference = dialog.reference
          GitBrancher.getInstance(project).checkout(reference, true, listOf(repository), null)
        }
      }
    ),
  )

  private val _text = mutableStateOf("")
  val text: State<String> = _text

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

  private val speedSearchState = mutableStateOf(speedSearch, neverEqualPolicy())

  private val branchManager = project.service<GitBranchManager>()

  private val localBranchesComparator: Comparator<GitBranch> =
    getBranchComparator(listOf(repository), branchManager.getFavoriteBranches(GitBranchType.LOCAL), isPrefixGrouping = { false })
  private val remoteBranchesComparator: Comparator<GitBranch> =
    getBranchComparator(listOf(repository), branchManager.getFavoriteBranches(GitBranchType.LOCAL), isPrefixGrouping = { false })

  private val allLocalBranches = repository.localBranchesOrCurrent.sortedWith(localBranchesComparator)
  private val allRemoteBranches = repository.branches.remoteBranches.sortedWith(remoteBranchesComparator)

  val localBranches: StateFlow<List<GitBranch>> = createFilteredBranchesFlow(allLocalBranches)

  val remoteBranches: StateFlow<List<GitBranch>> = createFilteredBranchesFlow(allRemoteBranches)

  val preferredBranch: StateFlow<GitBranch?> = combine(localBranches, remoteBranches,
                                                       snapshotFlow { _text.value }) { localBranches, remoteBranches, _ ->
    val matcher = speedSearch.matcher as? MinusculeMatcher
    selectPreferredBranch(matcher, localBranches, remoteBranches)
  }.stateIn(
    coroutineScope,
    initialValue = selectPreferredBranch(speedSearch.matcher as? MinusculeMatcher, allLocalBranches, allRemoteBranches),
    started = SharingStarted.Eagerly
  )

  private val favoriteBranches = mutableStateOf(getCurrentFavoriteBranches(), neverEqualPolicy())

  private val branchManagerListener = object : DvcsBranchManager.DvcsBranchManagerListener {
    override fun branchFavoriteSettingsChanged() {
      favoriteBranches.value = getCurrentFavoriteBranches()
    }
  }

  init {
    project.messageBus.connect(coroutineScope).subscribe(DVCS_BRANCH_SETTINGS_CHANGED, branchManagerListener)
    coroutineScope.coroutineContext.job.invokeOnCompletion {
      speedSearch.removeChangeListener(speedSearchListener)
    }

    coroutineScope.launch {
      snapshotFlow { text.value }.collectLatest {
        speedSearchState.value = speedSearch
      }
    }
  }

  private fun getCurrentFavoriteBranches(): Map<GitBranchType, Set<String>> {
    return buildMap {
      for (type in GitBranchType.entries) {
        val favoriteBranches = branchManager.getFavoriteBranches(type)
        put(type, favoriteBranches[repository] ?: setOf())
      }
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
    return snapshotFlow { _text.value }.debounce(TEXT_DEBOUNCE).map {
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

  fun createBranchVm(branch: GitBranch): GitBranchComposeVm {
    val isFavorite = derivedStateOf {
      favoriteBranches.value[GitBranchType.of(branch)]?.contains(branch.name) == true
    }

    return GitBranchComposeVm(
      repository,
      branch,
      incomingOutgoingManager = GitBranchIncomingOutgoingManager.getInstance(project),
      isFavorite = isFavorite,
      toggleIsFavoriteState = {
        branchManager.setFavorite(GitBranchType.of(branch), repository, branch.name, !isFavorite.value)
      },
      isCurrent = repository.currentBranch == branch,
      speedSearch = speedSearchState
    )
  }

  class BranchesAction(
    val title: @Nls String,
    val icon: String?,
    val iconClass: Class<*>?,
    val action: () -> Unit
  )

  companion object {
    private const val TEXT_DEBOUNCE = 100L
  }
}