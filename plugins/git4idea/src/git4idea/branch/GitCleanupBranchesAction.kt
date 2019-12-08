// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.branch

import com.intellij.dvcs.DvcsUtil
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.keymap.KeymapUtil.getActiveKeymapShortcuts
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.*
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.speedSearch.NameFilteringListModel
import com.intellij.ui.speedSearch.SpeedSearch
import com.intellij.util.ContentsUtil
import com.intellij.util.ThreeState
import com.intellij.util.ThreeState.UNSURE
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.vcs.log.VcsLogProperties
import com.intellij.vcs.log.data.DataPackChangeListener
import com.intellij.vcs.log.impl.VcsLogContentUtil
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.util.exclusiveCommits
import com.intellij.vcs.log.util.findBranch
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import com.intellij.vcs.log.visible.filters.VcsLogUserFilterImpl
import git4idea.config.GitSharedSettings
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import gnu.trove.TIntHashSet
import java.awt.BorderLayout
import java.awt.event.KeyEvent
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent

private val LOG = logger<GitCleanupBranchesAction>()

class GitCleanupBranchesAction : DumbAwareAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    val branches = getAllBranches(project)

    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.VCS)
    val ui = BranchesUi(project, VcsProjectLog.getInstance(project), branches)
    val content = ContentFactory.SERVICE.getInstance().createContent(ui.getComponent(), "Cleanup Branches", true)
    ContentsUtil.addContent(toolWindow.contentManager, content, true)
    Disposer.register(content, ui)
    toolWindow.activate(null, true, true)

    VcsLogContentUtil.runWhenLogIsReady(project) { _, _ ->
      ui.stopLoading()
    } // schedule initialization: need the log for other actions
  }
}

private data class BranchInfo(val branchName: String,
                              val isLocal: Boolean,
                              val repositories: List<GitRepository>) {
  var isMy: ThreeState = UNSURE
}

private class BranchesUi(val project: Project, val log: VcsProjectLog, initialBranches: List<BranchInfo>) : Disposable {
  val branches = HashSet<BranchInfo>(initialBranches)
  var showOnlyMy: Boolean = false

  val model = CollectionListModel<BranchInfo>(sort(branches))
  val list = BranchesList(project)
  val panel = MyPanel(list)
  val loadingPanel = JBLoadingPanel(BorderLayout(), this, ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS)

  val speedSearch = SpeedSearch()
  val filteringListModel = NameFilteringListModel<BranchInfo>(
    model, { it.branchName }, speedSearch::shouldBeShowing, { speedSearch.filter ?: "" })

  val changeListener = DataPackChangeListener {
    val newBranches = getAllBranches(project)
    branches.retainAll(newBranches)
    branches.addAll(newBranches)
    updateMyBranchesIfNeeded()
  }

  init {
    filteringListModel.setFilter { t: BranchInfo? -> t != null && speedSearch.shouldBeShowing(t.branchName)
                                                     && (!showOnlyMy || t.isMy == ThreeState.YES) }

    list.model = filteringListModel

    speedSearch.addChangeListener {
      filteringListModel.refilter()
    }

    log.dataManager?.addDataPackChangeListener(changeListener)
  }

  fun getComponent() : JComponent {
    val searchField = object : SearchTextField(false) {
      override fun preprocessEventForTextField(e: KeyEvent): Boolean {
        if (e.keyCode == KeyEvent.VK_DOWN || e.keyCode == KeyEvent.VK_UP) {
          list.dispatchEvent(e)
          return true
        }
        return false
      }
    }
    searchField.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        speedSearch.updatePattern(searchField.text)
      }
    })

    val diffAction = ShowBranchDiffAction()
    diffAction.registerCustomShortcutSet(getActiveKeymapShortcuts("Diff.ShowDiff"), panel)

    val deleteAction = DeleteBranchAction()
    val shortcuts = getActiveKeymapShortcuts("SafeDelete").shortcuts + getActiveKeymapShortcuts("EditorDeleteToLineStart").shortcuts
    deleteAction.registerCustomShortcutSet(CustomShortcutSet(*shortcuts), panel)

    createFocusFilterFieldAction(searchField)

    val showMyBranchesAction = ShowMyBranchesAction()

    val group = DefaultActionGroup()
    group.add(deleteAction)
    group.add(diffAction)
    group.add(showMyBranchesAction)
    val toolbar = ActionManager.getInstance().createActionToolbar("Git.Cleanup.Branches", group, false)
    toolbar.setTargetComponent(list)

    panel.addToTop(searchField)
    panel.addToLeft(toolbar.component)
    panel.addToCenter(ScrollPaneFactory.createScrollPane(list, true))

    loadingPanel.add(panel)
    loadingPanel.startLoading()
    return loadingPanel
  }

  private fun createFocusFilterFieldAction(searchField: SearchTextField) {
    DumbAwareAction.create { e ->
      val project = e.getRequiredData(CommonDataKeys.PROJECT)
      if (IdeFocusManager.getInstance(project).getFocusedDescendantFor(list) != null) {
        IdeFocusManager.getInstance(project).requestFocus(searchField, true)
      }
      else {
        IdeFocusManager.getInstance(project).requestFocus(list, true)
      }
    }.registerCustomShortcutSet(getActiveKeymapShortcuts("Find"), panel)
  }

  override fun dispose() {
    log.dataManager?.removeDataPackChangeListener(changeListener)
  }

  fun stopLoading() {
    loadingPanel.stopLoading()
  }

  fun updateMyBranchesIfNeeded() {
    if (showOnlyMy) {
      loadingPanel.startLoading()
      panel.isEnabled = false

      val branchesToCheck = branches.filter { it.isMy == UNSURE }
      object: Task.Backgroundable(project, "Calculating My Branches", true) {
        var myBranches : List<BranchInfo>? = null
        override fun run(indicator: ProgressIndicator) {
          myBranches = checkBranchesSynchronously(log, branchesToCheck, indicator)
        }

        override fun onSuccess() {
          if (myBranches == null) return

          for (branch in branches) {
            if (branch.isMy == UNSURE) {
              branch.isMy = if (myBranches!!.contains(branch)) ThreeState.YES else ThreeState.NO
            }
          }

          updateListAccordingToCurrentlyKnownBranches()
        }

        override fun onFinished() {
          panel.isEnabled = true
          loadingPanel.stopLoading()
        }
      }.queue()
    }
    else {
      updateListAccordingToCurrentlyKnownBranches()
    }
  }

  private fun updateListAccordingToCurrentlyKnownBranches() {
    preserveSelection {
      model.replaceAll(sort(branches))
      filteringListModel.refilter()
    }
  }

  private fun preserveSelection(action: () -> Unit) {
    val selectedValue = list.selectedValue
    val selectedIndex = list.selectedIndex

    action()

    var newSelectedIndex = -1
    for (i in 0 until list.model.size) {
      val element = list.model.getElementAt(i)
      if (element == selectedValue) {
        newSelectedIndex = i
        break
      }
    }
    list.selectedIndex = if (newSelectedIndex > 0) newSelectedIndex else selectedIndex
  }

  private inner class ShowMyBranchesAction : ToggleAction("Show My Branches", null, AllIcons.Actions.Find), DumbAware {
    override fun isSelected(e: AnActionEvent): Boolean {
      return showOnlyMy
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      showOnlyMy = state
      updateMyBranchesIfNeeded()
    }

    override fun update(e: AnActionEvent) {
      super.update(e)
      val log = VcsProjectLog.getInstance(project)
      val supportsIndexing = log.dataManager?.logProviders?.all {
        VcsLogProperties.SUPPORTS_INDEXING.getOrDefault(it.value) } ?: false

      val isGraphReady = log.dataManager?.dataPack?.isFull ?: false

      val allRootsIndexed = GitRepositoryManager.getInstance(project).repositories.all {
        log.dataManager?.index?.isIndexed(it.root) ?: false
      }

      e.presentation.isEnabled = supportsIndexing && isGraphReady && allRootsIndexed
      e.presentation.description = if (!supportsIndexing) {
        "Some of repositories doesn't support indexing."
      }
      else if (!allRootsIndexed) {
        "Not all repositories are indexed."
      }
      else if (!isGraphReady) {
        "The log is not ready yet, please wait a bit."
      }
      else {
        "A branch is 'My' if all exclusive commits of this branch are made by 'me', i.e. by current Git author."
      }
    }
  }
}

private class MyPanel(val list: BranchesList) : BorderLayoutPanel(), DataProvider {
  override fun getData(dataId: String): Any? {
    if (GIT_BRANCH.`is`(dataId)) {
      return list.selectedValue
    }
    return null
  }
}

private class BranchesList(project: Project) : JBList<BranchInfo>() {
  init {
    selectionMode = ListSelectionModel.SINGLE_SELECTION
    cellRenderer = object : ColoredListCellRenderer<BranchInfo>() {
      override fun customizeCellRenderer(list: JList<out BranchInfo>, value: BranchInfo?, index: Int, selected: Boolean, hasFocus: Boolean) {
        if (value == null) {
          return
        }
        append(value.branchName)
        if (value.repositories.size < GitRepositoryManager.getInstance(project).repositories.size) {
          append(" (${DvcsUtil.getShortNames(value.repositories)})", SimpleTextAttributes.GRAYED_ATTRIBUTES)
        }
      }
    }
  }
}

private class DeleteBranchAction : CleanupBranchesActionBase("Delete Branch", null, AllIcons.Actions.GC) {
  override fun actionPerformed(e: AnActionEvent) {
    val branch = e.getData(GIT_BRANCH)!!
    val project = e.project!!
    val brancher = GitBrancher.getInstance(project)

    if (branch.isLocal) {
      brancher.deleteBranch(branch.branchName, branch.repositories)
    }
    else {
      brancher.deleteRemoteBranch(branch.branchName, branch.repositories)
    }
  }
}

private class ShowBranchDiffAction : CleanupBranchesActionBase("Compare with Current", null, AllIcons.Actions.Diff) {
  override fun actionPerformed(e: AnActionEvent) {
    val branch = e.getData(GIT_BRANCH)!!
    val project = e.project!!

    VcsLogContentUtil.runWhenLogIsReady(project) { log, logManager ->
      val filters = VcsLogFilterObject.fromRange("HEAD", branch.branchName)
      log.tabsManager.openAnotherLogTab(logManager, VcsLogFilterObject.collection(filters))
    }
  }
}

private abstract class CleanupBranchesActionBase(text: String, description: String?, icon: Icon) :
  DumbAwareAction(text, description, icon) {

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabled = e.project != null && e.getData(GIT_BRANCH) != null
  }
}

private val GIT_BRANCH = DataKey.create<BranchInfo>("GitBranchKey")

private fun getAllBranches(project: Project): List<BranchInfo> {
  val settings = GitSharedSettings.getInstance(project)

  val localMap = mutableMapOf<String, MutableList<GitRepository>>()
  for (repo in GitRepositoryManager.getInstance(project).repositories) {
    for (branch in repo.branches.localBranches) {
      if (repo.currentBranch != branch) {
        val trackedBranch = branch.findTrackedBranch(repo)
        if (trackedBranch == null || !settings.isBranchProtected(trackedBranch.nameForRemoteOperations)) {
          localMap.computeIfAbsent(branch.name) { mutableListOf() }.add(repo)
        }
      }
    }
  }
  val local = localMap.map { (branchName, repos) -> BranchInfo(branchName, true, repos) }

  val remoteMap = mutableMapOf<String, MutableList<GitRepository>>()
  for (repo in GitRepositoryManager.getInstance(project).repositories) {
    for (remoteBranch in repo.branches.remoteBranches) {
      if (!settings.isBranchProtected(remoteBranch.nameForRemoteOperations)) {
        remoteMap.computeIfAbsent(remoteBranch.name) { mutableListOf() }.add(repo)
      }
    }
  }
  val remote = remoteMap.map { (branchName, repos) -> BranchInfo(branchName, false, repos) }

  return local + remote
}

private fun sort(branches: HashSet<BranchInfo>): List<BranchInfo> {
  return branches.sortedWith(Comparator { o1, o2 ->
    if (o1.isLocal && !o2.isLocal) -1
    else if (!o1.isLocal && o2.isLocal) 1
    else {
      o1.branchName.compareTo(o2.branchName)
    }
  })
}

private fun checkBranchesSynchronously(log: VcsProjectLog,
                                       branchesToCheck: Collection<BranchInfo>,
                                       indicator: ProgressIndicator): List<BranchInfo>? {
  val myCommits = findMyCommits(log)
  if (myCommits == null) return null

  indicator.isIndeterminate = false
  val branches = mutableListOf<BranchInfo>()
  for ((step, branch) in branchesToCheck.withIndex()) {
    indicator.fraction = step.toDouble() / branchesToCheck.size

    for (repo in branch.repositories) {
      indicator.checkCanceled()
      if (isMyBranch(log, branch.branchName, repo, myCommits)) {
        branches.add(branch)
      }
    }
  }

  return branches
}

fun findMyCommits(log: VcsProjectLog): Set<Int>? {
  val filterByMe = VcsLogFilterObject.fromUserNames(listOf(VcsLogUserFilterImpl.ME), log.dataManager!!)
  return log.dataManager!!.index.dataGetter!!.filter(listOf(filterByMe))
}

private fun isMyBranch(log: VcsProjectLog,
                       branchName: String,
                       repo: GitRepository,
                       myCommits: Set<Int>): Boolean {
  // branch is "my" if all its exclusive commits are made by me
  val exclusiveCommits = findExclusiveCommits(log, branchName, repo) ?: return false
  if (exclusiveCommits.isEmpty) return false

  for (commit in exclusiveCommits) {
    if (!myCommits.contains(commit)) {
      LOG.debug("Commit ${log.dataManager!!.storage.getCommitId(commit)} is not mine")
      return false
    }
  }

  return true
}

private fun findExclusiveCommits(log: VcsProjectLog, branchName: String, repo: GitRepository): TIntHashSet? {
  val dataPack = log.dataManager!!.dataPack

  val ref = dataPack.findBranch(branchName, repo.root) ?: return null
  if (!ref.type.isBranch) return null

  return dataPack.exclusiveCommits(ref, log.dataManager!!.storage)
}