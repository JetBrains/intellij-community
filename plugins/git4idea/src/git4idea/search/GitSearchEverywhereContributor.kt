// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.search

import com.intellij.icons.AllIcons
import com.intellij.ide.actions.searcheverywhere.*
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.ui.dsl.listCellRenderer.LcrInitParams
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.util.Processor
import com.intellij.util.text.Matcher
import com.intellij.util.ui.JBUI
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsRef
import com.intellij.vcs.log.data.DataPack
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.impl.VcsLogContentUtil
import com.intellij.vcs.log.impl.VcsLogNavigationUtil.jumpToCommit
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.ui.render.LabelIcon
import com.intellij.vcs.log.util.containsAll
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import git4idea.GitUtil
import git4idea.GitVcs
import git4idea.branch.GitBranchUtil
import git4idea.i18n.GitBundle
import git4idea.log.GitRefManager
import git4idea.repo.GitRepositoryManager
import git4idea.search.GitSearchEverywhereItemType.*
import java.util.function.Function
import javax.swing.ListCellRenderer

internal class GitSearchEverywhereContributor(private val project: Project) : WeightedSearchEverywhereContributor<Any>, DumbAware {
  private val filter = PersistentSearchEverywhereContributorFilter(
    GitSearchEverywhereItemType.values().asList(),
    project.service<GitSearchEverywhereFilterConfiguration>(),
    Function { it.displayName },
    Function { null }
  )

  override fun fetchWeightedElements(
    pattern: String,
    progressIndicator: ProgressIndicator,
    consumer: Processor<in FoundItemDescriptor<Any>>
  ) {
    if (!ProjectLevelVcsManager.getInstance(project).checkVcsIsActive(GitVcs.NAME)) return

    val logManager = VcsProjectLog.getInstance(project).logManager ?: return
    val dataManager = logManager.dataManager
    val storage = dataManager.storage
    val index = dataManager.index

    val dataPack = awaitFullLogDataPack(dataManager, progressIndicator) ?: return

    if (filter.isSelected(COMMIT_BY_HASH) && pattern.length >= 7 && GitUtil.isHashString(pattern, false)) {
      storage.findCommitId {
        progressIndicator.checkCanceled()
        it.hash.asString().startsWith(pattern, true) && dataPack.containsAll(listOf(it), storage)
      }?.let { commitId ->
        val id = storage.getCommitIndex(commitId.hash, commitId.root)
        dataManager.miniDetailsGetter.loadCommitsDataSynchronously(listOf(id), progressIndicator) { _, data ->
          consumer.process(FoundItemDescriptor(data, COMMIT_BY_HASH.weight))
        }
      }
    }

    val matcher = NameUtil.buildMatcher("*$pattern")
      .withCaseSensitivity(NameUtil.MatchingCaseSensitivity.NONE)
      .typoTolerant()
      .build()

    dataPack.refsModel.stream().forEach {
      progressIndicator.checkCanceled()
      when (it.type) {
        GitRefManager.LOCAL_BRANCH, GitRefManager.HEAD -> processRefOfType(it, LOCAL_BRANCH, matcher, consumer)
        GitRefManager.REMOTE_BRANCH -> processRefOfType(it, REMOTE_BRANCH, matcher, consumer)
        GitRefManager.TAG -> processRefOfType(it, TAG, matcher, consumer)
      }
    }

    if (filter.isSelected(COMMIT_BY_MESSAGE) && Registry.`is`("git.search.everywhere.commit.by.message")) {
      if (pattern.length < 3) return

      val allRootsIndexed = GitRepositoryManager.getInstance(project).repositories.all { index.isIndexed(it.root) }
      if (!allRootsIndexed) return

      index.dataGetter?.filterMessages(VcsLogFilterObject.fromPattern(pattern)) { commitIdx ->
        progressIndicator.checkCanceled()
        dataManager.miniDetailsGetter.loadCommitsDataSynchronously(listOf(commitIdx), progressIndicator) { _, data ->
          consumer.process(FoundItemDescriptor(data, COMMIT_BY_MESSAGE.weight))
        }
      }
    }
  }

  private fun processRefOfType(ref: VcsRef, type: GitSearchEverywhereItemType,
                               matcher: Matcher, consumer: Processor<in FoundItemDescriptor<Any>>) {
    if (!filter.isSelected(type)) return
    if (matcher.matches(ref.name)) consumer.process(FoundItemDescriptor(ref, type.weight))
  }

  private fun awaitFullLogDataPack(dataManager: VcsLogData, indicator: ProgressIndicator): DataPack? {
    if (!Registry.`is`("vcs.log.keep.up.to.date")) return null
    var dataPack: DataPack
    do {
      indicator.checkCanceled()
      dataPack = dataManager.dataPack
    }
    while (!dataPack.isFull && Thread.sleep(1000) == Unit)
    return dataPack
  }

  private val renderer = listCellRenderer<Any> {
    val value = this.value
    val iconBg = selectionColor ?: JBUI.CurrentTheme.List.BACKGROUND

    icon(
      if (value is VcsRef) LabelIcon(list, JBUI.scale(16), iconBg, listOf(value.type.backgroundColor)) else AllIcons.Vcs.CommitNode)

    text(when (value) {
           is VcsRef -> value.name
           is VcsCommitMetadata -> value.subject
           else -> ""
         }) {
      align = LcrInitParams.Align.LEFT
    }

    @NlsSafe
    val rightText = when (value) {
      is VcsRef -> getTrackingRemoteBranchName(value)
      is VcsCommitMetadata -> value.id.toShortString()
      else -> null
    }
    if (rightText != null) {
      text(rightText) {
        foreground = greyForeground
      }
    }
  }

  @NlsSafe
  private fun getTrackingRemoteBranchName(vcsRef: VcsRef): String? {
    if (vcsRef.type != GitRefManager.LOCAL_BRANCH) {
      return null
    }
    val repository = GitRepositoryManager.getInstance(project).getRepositoryForRootQuick(vcsRef.root) ?: return null
    return GitBranchUtil.getTrackInfo(repository, vcsRef.name)?.remoteBranch?.name
  }

  override fun getElementsRenderer(): ListCellRenderer<in Any> = renderer

  override fun processSelectedItem(selected: Any, modifiers: Int, searchText: String): Boolean {
    val hash: Hash?
    val root: VirtualFile?
    when (selected) {
      is VcsRef -> {
        hash = selected.commitHash
        root = selected.root
      }
      is VcsCommitMetadata -> {
        hash = selected.id
        root = selected.root
      }
      else -> {
        hash = null
        root = null
      }
    }

    if (hash != null && root != null) {
      VcsLogContentUtil.runInMainLog(project) { it.jumpToCommit(hash, root, false, true) }
      return true
    }
    return false
  }

  override fun getActions(onChanged: Runnable) = listOf(SearchEverywhereFiltersAction(filter, onChanged))

  override fun getSearchProviderId() = "Vcs.Git"
  override fun getGroupName() = GitBundle.message("search.everywhere.group.name")
  override fun getFullGroupName() = GitBundle.message("search.everywhere.group.full.name")

  // higher weight -> lower position
  override fun getSortWeight() = 500
  override fun showInFindResults() = false

  override fun isShownInSeparateTab(): Boolean {
    return AdvancedSettings.getBoolean("git.search.everywhere.tab.enabled")
           && ProjectLevelVcsManager.getInstance(project).checkVcsIsActive(GitVcs.NAME)
           && VcsProjectLog.getInstance(project).logManager != null
  }

  class Factory : SearchEverywhereContributorFactory<Any> {
    override fun createContributor(initEvent: AnActionEvent): GitSearchEverywhereContributor {
      val project = initEvent.getRequiredData(CommonDataKeys.PROJECT)
      return GitSearchEverywhereContributor(project)
    }
  }
}
