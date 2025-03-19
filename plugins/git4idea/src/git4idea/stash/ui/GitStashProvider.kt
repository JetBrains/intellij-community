// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.stash.ui

import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.dvcs.ui.RepositoryChangesBrowserNode
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ClearableLazyValue
import com.intellij.openapi.util.Disposer
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
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData.allUnder
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.FontUtil
import com.intellij.util.containers.JBIterable
import com.intellij.util.text.DateFormatUtil
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.ui.render.LabelIconCache
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import git4idea.stash.GitStashCache
import git4idea.stash.GitStashTracker
import git4idea.stash.GitStashTrackerListener
import git4idea.stash.isNotEmpty
import git4idea.ui.StashInfo
import git4idea.ui.StashInfo.Companion.branchName
import git4idea.ui.StashInfo.Companion.subject
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import javax.swing.JComponent

class GitStashProvider(val project: Project, parent: Disposable) : SavedPatchesProvider<StashInfo>, Disposable {
  private val iconCache = LabelIconCache()

  private val stashTracker get() = project.service<GitStashTracker>()
  private val stashCache: GitStashCache get() = project.service()

  override val dataClass: Class<StashInfo> get() = StashInfo::class.java
  override val tag: ChangesBrowserNode.Tag = GitBundleTag("stash.root.node.title")
  override val applyAction: AnAction get() = ActionManager.getInstance().getAction(GIT_STASH_APPLY_ACTION)
  override val popAction: AnAction get() = ActionManager.getInstance().getAction(GIT_STASH_POP_ACTION)

  init {
    Disposer.register(parent, this)
    stashTracker.addListener(object : GitStashTrackerListener {
      override fun stashesUpdated() {
        stashCache.preloadStashes()
      }
    }, this)
    stashCache.preloadStashes()
  }

  override fun subscribeToPatchesListChanges(disposable: Disposable, listener: () -> Unit) {
    stashTracker.addListener(object : GitStashTrackerListener {
      override fun stashesUpdated() {
        listener()
      }
    }, disposable)
  }

  override fun isEmpty() = !stashTracker.isNotEmpty()

  override fun buildPatchesTree(modelBuilder: TreeModelBuilder, showRootNode: Boolean) {
    val stashesMap = stashTracker.stashes
    val stashesRoot = if (showRootNode) {
      SavedPatchesTree.TagWithCounterChangesBrowserNode(tag).also { modelBuilder.insertSubtreeRoot(it) }
    }
    else {
      modelBuilder.myRoot
    }
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

  override fun uiDataSnapshot(sink: DataSink, selectedObjects: Iterable<SavedPatchesProvider.PatchObject<*>>) {
    sink[STASH_INFO] = JBIterable.from(selectedObjects)
      .map(SavedPatchesProvider.PatchObject<*>::data)
      .filter(dataClass)
      .toList()
  }

  override fun dispose() {
    stashCache.clear()
  }

  class GitBundleTag(@field:PropertyKey(resourceBundle = GitBundle.BUNDLE) private val key: String) : ChangesBrowserNode.Tag {
    override fun toString(): @Nls String = GitBundle.message(key)
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
      allUnder(this).userObjects(StashObject::class.java).size
    }

    override fun getCountText() = FontUtil.spaceAndThinSpace() + stashCount.value
    override fun resetCounters() {
      super.resetCounters()
      stashCount.drop()
    }
  }

  inner class StashObject(override val data: StashInfo) : SavedPatchesProvider.PatchObject<StashInfo> {
    override fun cachedChanges(): Collection<SavedPatchesProvider.ChangeObject>? {
      return stashCache.getCachedData(data)?.toChangeObjects()
    }

    override fun loadChanges(): CompletableFuture<SavedPatchesProvider.LoadingResult>? {
      val loadStashData = stashCache.loadStashData(data)
      val processResults = loadStashData?.thenApply { stashData ->
        when (stashData) {
          is GitStashCache.StashData.Changes -> {
            SavedPatchesProvider.LoadingResult.Changes(stashData.toChangeObjects())
          }
          is GitStashCache.StashData.Error -> SavedPatchesProvider.LoadingResult.Error(stashData.error)
        }
      }
      processResults?.propagateCancellationTo(loadStashData)
      return processResults
    }

    private fun GitStashCache.StashData.Changes.toChangeObjects(): List<GitStashChange> {
      val stashChanges = changes.map { GitStashChange(it, null, data.stash) }
      val otherChanges = parentCommits.flatMap { parent ->
        val tag: ChangesBrowserNode.Tag = MyTag(StringUtil.capitalize(parent.subject.substringBefore(":")), parent.id)
        parent.changes.map { GitStashChange(it, tag, data.stash) }
      }
      return stashChanges + otherChanges
    }

    override fun getDiffPreviewTitle(changeName: String?): String {
      return changeName?.let { name ->
        GitBundle.message("stash.editor.diff.preview.id.change.title", data.stash.capitalize(), name)
      } ?: GitBundle.message("stash.editor.diff.preview.empty.title")
    }

    override fun getLabelComponent(tree: ChangesTree, row: Int, selected: Boolean): JComponent? {
      val branchName = data.branchName ?: return null

      val painter = GitStashBranchComponent(tree, iconCache)
      painter.customise(branchName, data.root, row, selected)
      return painter
    }
  }

  class GitStashChange(private val change: Change, private val changeTag: ChangesBrowserNode.Tag?, private val stashName: String) : SavedPatchesProvider.ChangeObject {
    override fun createDiffRequestProducer(project: Project?): ChangeDiffRequestChain.Producer? {
      return ChangeDiffRequestProducer.create(project, change, prepareChangeContext())
    }

    override fun createDiffWithLocalRequestProducer(project: Project?, useBeforeVersion: Boolean): ChangeDiffRequestChain.Producer? {
      val changeWithLocal = ShowDiffWithLocalAction.getChangeWithLocal(change, useBeforeVersion, useBeforeVersion) ?: return null
      return ChangeDiffRequestProducer.create(project, changeWithLocal, prepareChangeContext())
    }

    override fun asChange(): Change = change

    override fun getFilePath(): FilePath = ChangesUtil.getFilePath(change)

    override val originalFilePath: FilePath?
      get() = ChangesUtil.getBeforePath(change)

    override fun getFileStatus(): FileStatus = change.fileStatus

    override fun getTag(): ChangesBrowserNode.Tag? = changeTag

    private fun prepareChangeContext(): Map<Key<*>, Any> {
      val context = mutableMapOf<Key<*>, Any>()
      changeTag?.let { context[ChangeDiffRequestProducer.TAG_KEY] = it }
      context[DiffUserDataKeysEx.VCS_DIFF_RIGHT_CONTENT_TITLE] = StringUtil.capitalize(stashName)
      return context
    }
  }

  companion object {
    private const val GIT_STASH_APPLY_ACTION = "Git.Stash.Apply"
    private const val GIT_STASH_POP_ACTION = "Git.Stash.Pop"

    fun CompletableFuture<*>.propagateCancellationTo(future: CompletableFuture<*>) {
      whenComplete { _, t ->
        if (t is CancellationException) future.cancel(false)
      }
    }
  }
}
