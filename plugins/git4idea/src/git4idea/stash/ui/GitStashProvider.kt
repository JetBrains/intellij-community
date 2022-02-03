// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.stash.ui

import com.intellij.dvcs.ui.RepositoryChangesBrowserNode
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ClearableLazyValue
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.actions.ShowDiffWithLocalAction
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import com.intellij.openapi.vcs.changes.savedPatches.SavedPatchesProvider
import com.intellij.openapi.vcs.changes.savedPatches.SavedPatchesTree
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.FontUtil
import com.intellij.util.text.DateFormatUtil
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.ui.render.LabelIconCache
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import git4idea.stash.GitStashCache
import git4idea.stash.GitStashTracker
import git4idea.stash.GitStashTrackerListener
import git4idea.ui.StashInfo
import git4idea.ui.StashInfo.Companion.subject
import org.jetbrains.annotations.Nls
import java.util.concurrent.CompletableFuture

class GitStashProvider(val project: Project) : SavedPatchesProvider<StashInfo> {
  private val iconCache = LabelIconCache()

  private val stashTracker get() = project.service<GitStashTracker>()
  private val stashCache: GitStashCache get() = project.service()

  override val dataClass: Class<StashInfo> get() = StashInfo::class.java
  override val dataKey: DataKey<List<StashInfo>> get() = STASH_INFO

  override val applyAction: AnAction get() = ActionManager.getInstance().getAction(GIT_STASH_APPLY_ACTION)
  override val popAction: AnAction get() = ActionManager.getInstance().getAction(GIT_STASH_POP_ACTION)

  override fun subscribeToPatchesListChanges(disposable: Disposable, listener: () -> Unit) {
    stashTracker.addListener(object : GitStashTrackerListener {
      override fun stashesUpdated() {
        listener()
      }
    }, disposable)
  }

  override fun buildPatchesTree(modelBuilder: TreeModelBuilder) {
    val stashesMap = stashTracker.stashes
    val stashesRoot = SavedPatchesTree.TagWithCounterChangesBrowserNode(GitBundle.message("stash.root.node.title"))
    modelBuilder.insertSubtreeRoot(stashesRoot)
    for ((root, stashesList) in stashesMap) {
      val rootNode = if (stashesMap.size > 1 &&
                         !(stashesList is GitStashTracker.Stashes.Loaded && stashesList.stashes.isEmpty())) {
        createRootNode(root)?.also { modelBuilder.insertSubtreeRoot(it, stashesRoot) } ?: stashesRoot
      }
      else {
        stashesRoot
      }

      when (stashesList) {
        is GitStashTracker.Stashes.Error -> {
          modelBuilder.insertErrorNode(stashesList.error, rootNode)
        }
        is GitStashTracker.Stashes.Loaded -> {
          for (stash in stashesList.stashes) {
            modelBuilder.insertSubtreeRoot(StashInfoChangesBrowserNode(StashObject(stash)), rootNode)
          }
        }
      }
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

  private data class MyTag(@Nls private val text: String, private val hash: Hash) : ChangesBrowserNode.Tag {
    override fun toString(): String = text
  }

  private class StashInfoChangesBrowserNode(private val stash: StashObject) : ChangesBrowserNode<StashObject>(stash) {
    override fun render(renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
      renderer.append(stash.data.subject)
      renderer.toolTipText = VcsBundle.message("saved.patch.created.on.date.at.time.tooltip",
                                               stash.data.stash,
                                               DateFormatUtil.formatDate(stash.data.authorTime),
                                               DateFormatUtil.formatTime(stash.data.authorTime))
    }

    override fun getTextPresentation(): String = stash.data.subject
  }

  private class StashRepositoryChangesBrowserNode(repository: GitRepository) : RepositoryChangesBrowserNode(repository) {
    private val stashCount = ClearableLazyValue.create {
      VcsTreeModelData.children(this).userObjects(StashObject::class.java).size
    }

    override fun getCountText() = FontUtil.spaceAndThinSpace() + stashCount.value
    override fun resetCounters() {
      super.resetCounters()
      stashCount.drop()
    }
  }

  inner class StashObject(override val data: StashInfo) : SavedPatchesProvider.PatchObject<StashInfo> {
    override fun loadChanges(): CompletableFuture<SavedPatchesProvider.LoadingResult>? {
      return stashCache.loadStashData(data)?.thenApply { stashData ->
        when (stashData) {
          is GitStashCache.StashData.Changes -> {
            val stashChanges = stashData.changes.map { GitStashChange(it, null) }
            val otherChanges = stashData.parentCommits.flatMap { parent ->
              val tag: ChangesBrowserNode.Tag = MyTag(StringUtil.capitalize(parent.subject.substringBefore(":")), parent.id)
              parent.changes.map { GitStashChange(it, tag) }
            }
            SavedPatchesProvider.LoadingResult.Changes(stashChanges + otherChanges)
          }
          is GitStashCache.StashData.Error -> SavedPatchesProvider.LoadingResult.Error(stashData.error)
        }
      }
    }

    override fun getDiffPreviewTitle(changeName: String?): String {
      return changeName?.let { name ->
        GitBundle.message("stash.editor.diff.preview.id.change.title", data.stash.capitalize(), name)
      } ?: GitBundle.message("stash.editor.diff.preview.empty.title")
    }

    override fun createPainter(tree: ChangesTree,
                               renderer: ChangesTreeCellRenderer,
                               row: Int,
                               selected: Boolean): SavedPatchesProvider.PatchObject.Painter? {
      val painter = GitStashPainter(tree, renderer, iconCache)
      painter.customise(data, row, selected)
      return painter
    }
  }

  class GitStashChange(private val change: Change, private val changeTag: ChangesBrowserNode.Tag?) : SavedPatchesProvider.ChangeObject {
    override fun createDiffRequestProducer(project: Project?): ChangeDiffRequestChain.Producer? {
      return ChangeDiffRequestProducer.create(project, change, prepareChangeContext())
    }

    override fun createDiffWithLocalRequestProducer(project: Project?, useBeforeVersion: Boolean): ChangeDiffRequestChain.Producer? {
      val changeWithLocal = ShowDiffWithLocalAction.getChangeWithLocal(change, useBeforeVersion) ?: return null
      return ChangeDiffRequestProducer.create(project, changeWithLocal, prepareChangeContext())
    }

    override fun asChange(): Change = change

    override fun getFilePath(): FilePath = ChangesUtil.getFilePath(change)

    override fun getFileStatus(): FileStatus = change.fileStatus

    override fun getTag(): ChangesBrowserNode.Tag? = changeTag

    private fun prepareChangeContext(): Map<Key<*>, Any> {
      val context = mutableMapOf<Key<*>, Any>()
      changeTag?.let { context[ChangeDiffRequestProducer.TAG_KEY] = it }
      return context
    }
  }

  companion object {
    private const val GIT_STASH_APPLY_ACTION = "Git.Stash.Apply"
    private const val GIT_STASH_POP_ACTION = "Git.Stash.Pop"
  }
}
