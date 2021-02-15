// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.search

import com.intellij.icons.AllIcons
import com.intellij.ide.actions.searcheverywhere.*
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.util.Processor
import com.intellij.util.text.Matcher
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsRef
import com.intellij.vcs.log.data.DataPack
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.impl.VcsLogContentUtil
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.ui.render.LabelIcon
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.vcs.log.util.containsAll
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import git4idea.GitVcs
import git4idea.branch.GitBranchUtil
import git4idea.i18n.GitBundle
import git4idea.log.GitRefManager
import git4idea.repo.GitRepositoryManager
import git4idea.search.GitSearchEverywhereItemType.*
import java.awt.BorderLayout
import java.awt.Component
import java.util.function.Function
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
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

    if (filter.isSelected(COMMIT_BY_HASH) && pattern.length >= 7 && VcsLogUtil.HASH_REGEX.matcher(pattern).matches()) {
      storage.findCommitId {
        progressIndicator.checkCanceled()
        it.hash.asString().startsWith(pattern, true) && dataPack.containsAll(listOf(it), storage)
      }?.let { commitId ->
        val id = storage.getCommitIndex(commitId.hash, commitId.root)
        dataManager.miniDetailsGetter.loadCommitsData(listOf(id), {
          consumer.process(FoundItemDescriptor(it, COMMIT_BY_HASH.weight))
        }, progressIndicator)
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
        dataManager.miniDetailsGetter.loadCommitsData(listOf(commitIdx), {
          consumer.process(FoundItemDescriptor(it, COMMIT_BY_MESSAGE.weight))
        }, progressIndicator)
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

  private val renderer = object : ListCellRenderer<Any> {

    private val leftLabel = JLabel().apply {
      border = JBUI.Borders.empty(0, 8, 0, 2)
    }
    private val rightLabel = JLabel().apply {
      border = JBUI.Borders.empty(0, 2, 0, 8)
    }

    private val panel = JPanel(BorderLayout(0, 0)).apply {
      add(leftLabel, BorderLayout.CENTER)
      add(rightLabel, BorderLayout.EAST)
    }

    override fun getListCellRendererComponent(
      list: JList<out Any>,
      value: Any?, index: Int,
      isSelected: Boolean, cellHasFocus: Boolean
    ): Component {
      panel.background = UIUtil.getListBackground(isSelected, cellHasFocus)
      leftLabel.apply {
        font = list.font
        text = when (value) {
          is VcsRef -> value.name
          is VcsCommitMetadata -> value.subject
          else -> null
        }
        icon = when (value) {
          is VcsRef -> LabelIcon(this, JBUI.scale(16), background, listOf(value.type.backgroundColor))
          else -> AllIcons.Vcs.CommitNode
        }
        foreground = UIUtil.getListForeground(isSelected, cellHasFocus)
      }

      rightLabel.apply {
        font = list.font
        text = when (value) {
          is VcsRef -> getTrackingRemoteBranchName(value)
          is VcsCommitMetadata -> value.id.toShortString()
          else -> null
        }
        foreground = if (!isSelected) UIUtil.getInactiveTextColor() else UIUtil.getListForeground(isSelected, cellHasFocus)
      }
      return panel
    }

    @NlsSafe
    private fun getTrackingRemoteBranchName(vcsRef: VcsRef): String? {
      if (vcsRef.type != GitRefManager.LOCAL_BRANCH) {
        return null
      }
      val repository = GitRepositoryManager.getInstance(project).getRepositoryForRootQuick(vcsRef.root) ?: return null
      return GitBranchUtil.getTrackInfo(repository, vcsRef.name)?.remoteBranch?.name
    }
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
      VcsLogContentUtil.runInMainLog(project) {
        it.vcsLog.jumpToCommit(hash, root)
      }
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
    return ProjectLevelVcsManager.getInstance(project).checkVcsIsActive(GitVcs.NAME) &&
           VcsProjectLog.getInstance(project).logManager != null
  }

  override fun getDataForItem(element: Any, dataId: String): Any? = null

  companion object {
    class Factory : SearchEverywhereContributorFactory<Any> {
      override fun createContributor(initEvent: AnActionEvent): GitSearchEverywhereContributor {
        val project = initEvent.getRequiredData(CommonDataKeys.PROJECT)
        return GitSearchEverywhereContributor(project)
      }
    }
  }
}
