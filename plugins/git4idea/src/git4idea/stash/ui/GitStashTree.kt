// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.stash.ui

import com.intellij.dvcs.ui.RepositoryChangesBrowserNode
import com.intellij.ide.IdeBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.SimpleTextAttributes
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepositoryManager
import git4idea.stash.GitStashCache
import git4idea.stash.GitStashTracker
import git4idea.stash.GitStashTrackerListener
import git4idea.ui.StashInfo
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener

class GitStashTree(project: Project, parentDisposable: Disposable) : ChangesTree(project, false, false) {
  private val stashTracker get() = project.service<GitStashTracker>()
  private val stashCache get() = project.service<GitStashCache>()

  init {
    isKeepTreeState = true
    setEmptyText(GitBundle.message("stash.empty.text"))

    project.messageBus.connect(parentDisposable).subscribe(GitStashCache.GIT_STASH_LOADED, object : GitStashCache.StashLoadedListener {
      override fun stashLoaded(stashInfo: StashInfo) {
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
        val node = event.path.lastPathComponent as? ChangesBrowserNode<*> ?: return
        val stashInfo = node.userObject as? StashInfo ?: return
        stashCache.loadStashData(stashInfo)
      }

      override fun treeCollapsed(event: TreeExpansionEvent) = Unit
    })
  }

  override fun rebuildTree() {
    val modelBuilder = TreeModelBuilder(project, groupingSupport.grouping)
    val stashesMap = stashTracker.stashes
    for ((root, stashes) in stashesMap) {
      val rootNode = if (stashesMap.size > 1) {
        createRootNode(root).also { modelBuilder.insertSubtreeRoot(it) }
      }
      else {
        modelBuilder.myRoot
      }

      for (stash in stashes) {
        val stashNode = StashInfoChangesBrowserNode(stash)
        modelBuilder.insertSubtreeRoot(stashNode, rootNode)

        when (val stashData = stashCache.getCachedStashData(stash)) {
          is GitStashCache.StashData.ChangeList -> {
            modelBuilder.insertChanges(stashData.changeList.changes, stashNode)
          }
          is GitStashCache.StashData.Error -> {
            modelBuilder.insertSubtreeRoot(ChangesBrowserNode.createObject(stashData.error.localizedMessage), stashNode)
          }
          null -> {
            modelBuilder.insertSubtreeRoot(ChangesBrowserNode.createObject(IdeBundle.message("treenode.loading")), stashNode)
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

  override fun resetTreeState() {
    expandDefaults()
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
}