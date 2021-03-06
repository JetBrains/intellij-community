// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.stash.ui

import com.intellij.dvcs.ui.RepositoryChangesBrowserNode
import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.ActionUtil.performActionDumbAwareWithCallbacks
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.Processor
import com.intellij.vcs.log.Hash
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepositoryManager
import git4idea.stash.GitStashCache
import git4idea.stash.GitStashTracker
import git4idea.stash.GitStashTrackerListener
import git4idea.stash.ui.GitStashUi.Companion.GIT_STASH_UI_PLACE
import git4idea.ui.StashInfo
import org.jetbrains.annotations.NonNls
import java.util.stream.Stream
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.event.TreeSelectionEvent
import javax.swing.tree.TreePath
import kotlin.streams.toList

class GitStashTree(project: Project, parentDisposable: Disposable) : ChangesTree(project, false, false) {
  private val stashTracker get() = project.service<GitStashTracker>()
  private val stashCache get() = project.service<GitStashCache>()

  init {
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

    project.messageBus.connect(parentDisposable).subscribe(GitStashCache.GIT_STASH_LOADED, object : GitStashCache.StashLoadedListener {
      override fun stashLoaded(root: VirtualFile, hash: Hash) {
        rebuildTree()
      }
    })
    stashTracker.addListener(object : GitStashTrackerListener {
      override fun stashesUpdated() {
        rebuildTree()
      }
    }, parentDisposable)
    addTreeExpansionListener(object : TreeExpansionListener {
      override fun treeExpanded(event: TreeExpansionEvent) {
        loadStash(event.path)
      }

      override fun treeCollapsed(event: TreeExpansionEvent) = Unit
    })
    addTreeSelectionListener { event: TreeSelectionEvent ->
      loadStash(event.path)
    }
  }

  private fun loadStash(treePath: TreePath) {
    val node = treePath.lastPathComponent as? ChangesBrowserNode<*> ?: return
    val stashInfo = node.userObject as? StashInfo ?: return
    stashCache.loadStashData(stashInfo)
  }

  override fun rebuildTree() {
    val modelBuilder = TreeModelBuilder(project, groupingSupport.grouping)
    val stashesMap = stashTracker.stashes
    for ((root, stashesList) in stashesMap) {
      val rootNode = if (stashesMap.size > 1) {
        createRootNode(root).also { modelBuilder.insertSubtreeRoot(it) }
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
            val stashNode = StashInfoChangesBrowserNode(stash)
            modelBuilder.insertSubtreeRoot(stashNode, rootNode)

            when (val stashData = stashCache.getCachedStashData(stash)) {
              is GitStashCache.StashData.ChangeList -> {
                modelBuilder.insertChanges(stashData.changeList.changes, stashNode)
              }
              is GitStashCache.StashData.Error -> {
                modelBuilder.insertErrorNode(stashData.error, stashNode)
              }
              null -> {
                modelBuilder.insertSubtreeRoot(ChangesBrowserStringNode(IdeBundle.message("treenode.loading")), stashNode)
              }
            }
          }
        }
      }
    }
    updateTreeModel(modelBuilder.build())
  }

  private fun createRootNode(root: VirtualFile): ChangesBrowserNode<*> {
    val repository = GitRepositoryManager.getInstance(project).getRepositoryForRootQuick(root)
                     ?: return ChangesBrowserNode.createFile(project, root)
    return RepositoryChangesBrowserNode(repository)
  }

  private fun TreeModelBuilder.insertErrorNode(error: VcsException, parent: ChangesBrowserNode<*>) {
    val errorNode = ChangesBrowserStringNode(error.localizedMessage, SimpleTextAttributes.ERROR_ATTRIBUTES)
    insertSubtreeRoot(errorNode, parent)
  }

  override fun resetTreeState() {
    expandDefaults()
  }

  override fun installGroupingSupport(): ChangesGroupingSupport {
    val groupingSupport = object : ChangesGroupingSupport(myProject, this, false) {
      override fun isAvailable(groupingKey: String): Boolean {
        return groupingKey != REPOSITORY_GROUPING && super.isAvailable(groupingKey)
      }
    }
    installGroupingSupport(this, groupingSupport, GROUPING_PROPERTY_NAME, *DEFAULT_GROUPING_KEYS)
    return groupingSupport
  }

  override fun getData(dataId: String): Any? {
    if (STASH_INFO.`is`(dataId)) return selectedStashes().toList()
    if (GIT_STASH_TREE_FLAG.`is`(dataId)) return true
    return VcsTreeModelData.getData(myProject, this, dataId) ?: super.getData(dataId)
  }

  private fun selectedStashes(): Stream<StashInfo> {
    return VcsTreeModelData.selected(this).userObjectsStream(StashInfo::class.java)
  }

  class StashInfoChangesBrowserNode(private val stash: StashInfo) : ChangesBrowserNode<StashInfo>(stash) {
    override fun render(renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
      renderer.append(stash.stash, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
      renderer.append(": ")
      stash.branch?.let {
        renderer.append(it, SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES)
        renderer.append(": ")
      }
      renderer.append(stash.message)
    }

    override fun getTextPresentation(): String = stash.stash
    override fun shouldExpandByDefault() = false
  }

  companion object {
    val GIT_STASH_TREE_FLAG = DataKey.create<Boolean>("GitStashTreeFlag")
    @NonNls
    private const val GROUPING_PROPERTY_NAME = "GitStash.ChangesTree.GroupingKeys"
  }
}