// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.stash.ui

import com.intellij.dvcs.ui.RepositoryChangesBrowserNode
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.ActionUtil.performActionDumbAwareWithCallbacks
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ClearableLazyValue
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.openapi.vcs.changes.ui.HoverChangesTree.Companion.getBackground
import com.intellij.openapi.vcs.changes.ui.HoverChangesTree.Companion.getRowHeight
import com.intellij.openapi.vcs.changes.ui.HoverChangesTree.Companion.getTransparentScrollbarWidth
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.FontUtil
import com.intellij.util.Processor
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.vcs.log.RefGroup
import com.intellij.vcs.log.VcsRef
import com.intellij.vcs.log.ui.render.LabelIconCache
import com.intellij.vcs.log.ui.render.LabelPainter
import git4idea.i18n.GitBundle
import git4idea.log.GitRefManager
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import git4idea.stash.GitStashTracker
import git4idea.stash.GitStashTrackerListener
import git4idea.stash.ui.GitStashUi.Companion.GIT_STASH_UI_PLACE
import git4idea.ui.StashInfo
import git4idea.ui.StashInfo.Companion.branchName
import git4idea.ui.StashInfo.Companion.subject
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.util.stream.Stream
import javax.swing.JComponent
import javax.swing.JTree
import kotlin.streams.toList

class GitStashTree(project: Project, parentDisposable: Disposable) : ChangesTree(project, false, false) {
  private val stashTracker get() = project.service<GitStashTracker>()

  init {
    val nodeRenderer = ChangesBrowserNodeRenderer(myProject, { isShowFlatten }, false)
    setCellRenderer(MyTreeRenderer(this, nodeRenderer))

    isKeepTreeState = true
    isScrollToSelection = false
    setEmptyText(GitBundle.message("stash.empty.text"))

    doubleClickHandler = Processor { e ->
      val diffAction = ActionManager.getInstance().getAction(IdeActions.ACTION_SHOW_DIFF_COMMON)

      val dataContext = DataManager.getInstance().getDataContext(this)
      val event = AnActionEvent.createFromAnAction(diffAction, e, GIT_STASH_UI_PLACE, dataContext)
      val isEnabled = ActionUtil.lastUpdateAndCheckDumb(diffAction, event, true)
      if (isEnabled) performActionDumbAwareWithCallbacks(diffAction, event)

      isEnabled
    }

    stashTracker.addListener(object : GitStashTrackerListener {
      override fun stashesUpdated() {
        rebuildTree()
      }
    }, parentDisposable)
  }

  override fun rebuildTree() {
    val modelBuilder = TreeModelBuilder(project, groupingSupport.grouping)
    val stashesMap = stashTracker.stashes
    for ((root, stashesList) in stashesMap) {
      val rootNode = if (stashesMap.size > 1 &&
                         !(stashesList is GitStashTracker.Stashes.Loaded && stashesList.stashes.isEmpty())) {
        createRootNode(root)?.also { modelBuilder.insertSubtreeRoot(it) } ?: modelBuilder.myRoot
      }
      else {
        modelBuilder.myRoot
      }

      when (stashesList) {
        is GitStashTracker.Stashes.Error -> {
          modelBuilder.insertErrorNode(stashesList.error, rootNode)
        }
        is GitStashTracker.Stashes.Loaded -> {
          for (stash in stashesList.stashes) {
            modelBuilder.insertSubtreeRoot(StashInfoChangesBrowserNode(stash), rootNode)
          }
        }
      }
    }
    updateTreeModel(modelBuilder.build())

    if (selectionCount == 0) {
      TreeUtil.selectFirstNode(this)
    }
  }

  private fun createRootNode(root: VirtualFile): ChangesBrowserNode<*>? {
    val repository = GitRepositoryManager.getInstance(project).getRepositoryForRootQuick(root) ?: return null
    return StashRepositoryChangesBrowserNode(repository)
  }

  private fun TreeModelBuilder.insertErrorNode(error: VcsException, parent: ChangesBrowserNode<*>) {
    val errorNode = ChangesBrowserStringNode(error.localizedMessage, SimpleTextAttributes.ERROR_ATTRIBUTES)
    insertSubtreeRoot(errorNode, parent)
  }

  override fun resetTreeState() {
    expandDefaults()
  }

  override fun installGroupingSupport(): ChangesGroupingSupport {
    return object : ChangesGroupingSupport(myProject, this, false) {
      override fun isAvailable(groupingKey: String): Boolean = false
    }
  }

  override fun getData(dataId: String): Any? {
    if (STASH_INFO.`is`(dataId)) return selectedStashes().toList()
    if (CommonDataKeys.PROJECT.`is`(dataId)) return myProject
    return super.getData(dataId)
  }

  private fun selectedStashes(): Stream<StashInfo> {
    return VcsTreeModelData.selected(this).userObjectsStream(StashInfo::class.java)
  }

  class StashInfoChangesBrowserNode(private val stash: StashInfo) : ChangesBrowserNode<StashInfo>(stash) {
    override fun render(renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
      renderer.append(stash.subject)
      renderer.toolTipText = GitBundle.message("stash.created.on.date.at.time.tooltip",
                                               stash.stash,
                                               DateFormatUtil.formatDate(stash.authorTime),
                                               DateFormatUtil.formatTime(stash.authorTime))
    }

    override fun getTextPresentation(): String = stash.subject
  }

  class StashRepositoryChangesBrowserNode(repository: GitRepository) : RepositoryChangesBrowserNode(repository) {
    private val stashCount = ClearableLazyValue.create {
      VcsTreeModelData.children(this).userObjects(StashInfo::class.java).size
    }

    override fun getCountText() = FontUtil.spaceAndThinSpace() + stashCount.value
    override fun resetCounters() {
      super.resetCounters()
      stashCount.drop()
    }
  }

  class MyTreeRenderer(val component: ChangesTree, renderer: ChangesBrowserNodeRenderer) : ChangesTreeCellRenderer(renderer) {
    private val labelRightGap = JBUI.scale(1)
    private val labelPainter = StashLabelPainter(component, LabelIconCache())

    override fun paint(g: Graphics) {
      super.paint(g)
      labelPainter.paint(g as Graphics2D)
    }

    override fun getTreeCellRendererComponent(tree: JTree,
                                              value: Any,
                                              selected: Boolean,
                                              expanded: Boolean,
                                              leaf: Boolean,
                                              row: Int,
                                              hasFocus: Boolean): Component {
      val rendererComponent = super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
      customizeBranchLabel(tree as ChangesTree, value as ChangesBrowserNode<*>, row, selected)
      return rendererComponent
    }

    private fun customizeBranchLabel(tree: ChangesTree,
                                     node: ChangesBrowserNode<*>,
                                     row: Int,
                                     selected: Boolean) {
      if (tree.expandableItemsHandler.expandedItems.contains(row)) {
        labelPainter.clearPainter()
        return
      }
      val stashInfo = (node.userObject as? StashInfo)
      val branchName = stashInfo?.branchName
      if (stashInfo == null || branchName == null) {
        labelPainter.clearPainter()
        return
      }

      val repository = GitRepositoryManager.getInstance(tree.project).getRepositoryForRootQuick(stashInfo.root)
      val isCurrentBranch = repository?.currentBranch?.name == branchName

      val nodeLocation = TreeUtil.getNodeRowX(tree, row) + tree.insets.left
      val availableWidth = tree.visibleRect.width - tree.getTransparentScrollbarWidth() -
                           (nodeLocation - tree.visibleRect.x).coerceAtLeast(0)
      labelPainter.customizePainter(tree.getBackground(row, selected), UIUtil.getLabelForeground(), selected, availableWidth,
                                    listOf(StashRefGroup(branchName, isCurrentBranch)))

      // label coordinates are calculated relative to the node location
      val labelEndLocation = tree.visibleRect.x + tree.visibleRect.width - nodeLocation
      val labelStartLocation = labelEndLocation - labelPainter.size.width - labelRightGap - tree.getTransparentScrollbarWidth()
      labelPainter.customizeLocation(labelStartLocation, labelEndLocation, tree.getRowHeight(this))
    }

    private class StashLabelPainter(component: JComponent, iconCache: LabelIconCache) : LabelPainter(component, iconCache) {
      private var startLocation: Int = 0
      private var endLocation: Int = 0
      private var rowHeight = 0

      fun customizeLocation(startLocation: Int, endLocation: Int, rowHeight: Int) {
        this.startLocation = startLocation
        this.endLocation = endLocation
        this.rowHeight = rowHeight
      }

      fun clearPainter() {
        myLabels.clear()
      }

      fun paint(g2: Graphics2D) {
        paint(g2, startLocation, 0, rowHeight)

        if (myLabels.isNotEmpty()) {
          // paint the space after the label
          val labelEnd = startLocation + size.width
          if (labelEnd != endLocation) {
            g2.color = myBackground
            g2.fillRect(labelEnd, 0, endLocation - labelEnd, rowHeight)
          }
        }
      }
    }

    private class StashRefGroup(private val branchName: @NlsSafe String, private val isCurrent: Boolean) : RefGroup {
      override fun getName() = branchName
      override fun getRefs() = mutableListOf<VcsRef>()
      override fun isExpanded() = false
      override fun getColors(): List<Color> {
        if (isCurrent) return listOf(GitRefManager.HEAD.backgroundColor,
                                     GitRefManager.LOCAL_BRANCH.backgroundColor)
        return listOf(GitRefManager.LOCAL_BRANCH.backgroundColor)
      }
    }
  }
}