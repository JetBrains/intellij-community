// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.stash.ui

import com.intellij.dvcs.ui.RepositoryChangesBrowserNode
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.ActionUtil.performActionDumbAwareWithCallbacks
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.Processor
import com.intellij.util.ui.tree.TreeUtil
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepositoryManager
import git4idea.stash.GitStashTracker
import git4idea.stash.GitStashTrackerListener
import git4idea.stash.ui.GitStashUi.Companion.GIT_STASH_UI_PLACE
import git4idea.ui.StashInfo
import java.util.stream.Stream
import kotlin.streams.toList

class GitStashTree(project: Project, parentDisposable: Disposable) : ChangesTree(project, false, false) {
  private val stashTracker get() = project.service<GitStashTracker>()

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
      renderer.append(stash.stash, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
      renderer.append(": ")
      stash.branch?.let {
        renderer.append(it, SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES)
        renderer.append(": ")
      }
      renderer.append(stash.message)
    }

    override fun getTextPresentation(): String = stash.stash
  }

  companion object {
    val GIT_STASH_TREE_FLAG = DataKey.create<Boolean>("GitStashTreeFlag")
  }
}