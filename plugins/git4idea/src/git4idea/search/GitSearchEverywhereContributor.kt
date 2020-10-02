// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.search

import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributorFactory
import com.intellij.ide.actions.searcheverywhere.WeightedSearchEverywhereContributor
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.list.LeftRightRenderer
import com.intellij.util.Processor
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UI
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
import git4idea.branch.GitBranchUtil
import git4idea.i18n.GitBundle
import git4idea.log.GitRefManager
import git4idea.repo.GitRepositoryManager
import javax.swing.JList
import javax.swing.ListCellRenderer

class GitSearchEverywhereContributor(private val project: Project) : WeightedSearchEverywhereContributor<Any>, DumbAware {

  private val COMMIT_BY_HASH_WEIGHT = 50
  private val LOCAL_BRANCH_WEIGHT = 40
  private val REMOTE_BRANCH_WEIGHT = 30
  private val TAG_WEIGHT = 20
  private val COMMIT_BY_MESSAGE_WEIGHT = 10

  override fun fetchWeightedElements(pattern: String,
                                     progressIndicator: ProgressIndicator,
                                     consumer: Processor<in FoundItemDescriptor<Any>>) {

    val logManager = VcsProjectLog.getInstance(project).logManager ?: return
    val dataManager = logManager.dataManager
    val storage = dataManager.storage
    val index = dataManager.index

    val dataPack = awaitFullLogDataPack(dataManager, progressIndicator) ?: return

    if (pattern.length >= 7 && VcsLogUtil.HASH_REGEX.matcher(pattern).matches()) {
      storage.findCommitId {
        progressIndicator.checkCanceled()
        it.hash.asString().startsWith(pattern, true) && dataPack.containsAll(listOf(it), storage)
      }?.let { commitId ->
        val id = storage.getCommitIndex(commitId.hash, commitId.root)
        dataManager.miniDetailsGetter.loadCommitsData(listOf(id), {
          consumer.process(FoundItemDescriptor(it, COMMIT_BY_HASH_WEIGHT))
        }, progressIndicator)
      }
    }

    val matcher = NameUtil.buildMatcher(pattern)
      .withCaseSensitivity(NameUtil.MatchingCaseSensitivity.NONE)
      .preferringStartMatches()
      .build()

    dataPack.refsModel.stream().forEach {
      progressIndicator.checkCanceled()
      if (matcher.matches(it.name)) {
        val weight = when (it.type) {
          GitRefManager.TAG -> TAG_WEIGHT
          GitRefManager.LOCAL_BRANCH -> LOCAL_BRANCH_WEIGHT
          GitRefManager.REMOTE_BRANCH -> REMOTE_BRANCH_WEIGHT
          GitRefManager.HEAD -> LOCAL_BRANCH_WEIGHT
          else -> REMOTE_BRANCH_WEIGHT
        }
        consumer.process(FoundItemDescriptor(it, weight))
      }
    }

    if (Registry.`is`("git.search.everywhere.commit.by.message")) {
      if (pattern.length < 3) return

      val allRootsIndexed = GitRepositoryManager.getInstance(project).repositories.all { index.isIndexed(it.root) }
      if (!allRootsIndexed) return

      index.dataGetter?.filterMessages(VcsLogFilterObject.fromPattern(pattern)) { commitIdx ->
        progressIndicator.checkCanceled()
        dataManager.miniDetailsGetter.loadCommitsData(listOf(commitIdx), {
          consumer.process(FoundItemDescriptor(it, COMMIT_BY_MESSAGE_WEIGHT))
        }, progressIndicator)
      }
    }
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

  override fun getElementsRenderer(): ListCellRenderer<in Any> = Renderer()

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

  override fun getSearchProviderId() = "Vcs.Git"
  override fun getGroupName() = GitBundle.message("search.everywhere.group.name")
  override fun getFullGroupName() = GitBundle.message("search.everywhere.group.full.name")
  override fun getSortWeight() = 1000
  override fun showInFindResults() = false
  override fun isShownInSeparateTab(): Boolean = true
  override fun getDataForItem(element: Any, dataId: String): Any? = null

  private inner class Renderer : LeftRightRenderer<Any>() {
    override val mainRenderer = object : SimpleListCellRenderer<Any>() {
      override fun customize(list: JList<out Any>, value: Any?, index: Int, selected: Boolean, hasFocus: Boolean) {
        text = when (value) {
          is VcsRef -> value.name
          is VcsCommitMetadata -> value.subject
          else -> null
        }
        icon = when (value) {
          is VcsRef -> LabelIcon(this, UI.scale(16), background, listOf(value.type.backgroundColor))
          else -> EmptyIcon.ICON_16
        }
        border = JBUI.Borders.empty(0, 2)
      }
    }

    override val rightRenderer = object : SimpleListCellRenderer<Any>() {
      override fun customize(list: JList<out Any>, value: Any?, index: Int, selected: Boolean, hasFocus: Boolean) {
        text = when (value) {
          is VcsRef -> getTrackingRemoteBranchName(value)
          is VcsCommitMetadata -> value.id.toShortString()
          else -> null
        }
        foreground = if (!selected) UIUtil.getInactiveTextColor() else UIUtil.getListForeground(selected, hasFocus)
      }
    }

    @NlsSafe
    private fun getTrackingRemoteBranchName(vcsRef: VcsRef): String? {
      if (vcsRef.type != GitRefManager.LOCAL_BRANCH) return null
      val repository = GitRepositoryManager.getInstance(project).getRepositoryForRootQuick(vcsRef.root) ?: return null
      return GitBranchUtil.getTrackInfo(repository, vcsRef.name)?.remoteBranch?.name;
    }
  }

  companion object {
    class Factory : SearchEverywhereContributorFactory<Any> {
      override fun createContributor(initEvent: AnActionEvent): GitSearchEverywhereContributor {
        val project = initEvent.getRequiredData(CommonDataKeys.PROJECT)
        return GitSearchEverywhereContributor(project)
      }
    }
  }
}
