// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.stash.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.DiffPreview
import com.intellij.openapi.vcs.changes.EditorTabPreview
import com.intellij.openapi.vcs.changes.actions.ShowDiffWithLocalAction
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserBase
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.ui.StatusText
import com.intellij.vcs.log.Hash
import git4idea.GitCommit
import git4idea.i18n.GitBundle
import git4idea.stash.GitStashCache
import git4idea.ui.StashInfo
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet
import org.jetbrains.annotations.Nls
import java.awt.Component
import java.util.concurrent.CompletableFuture
import javax.swing.tree.DefaultTreeModel

class GitStashChangesBrowser(project: Project, private val focusMainUi: (Component?) -> Unit,
                             parentDisposable: Disposable) : ChangesBrowserBase(project, false, false), Disposable {
  private val stashCache: GitStashCache get() = myProject.service()

  private var stashedChanges: Collection<Change> = emptyList()
  private var otherChanges: Map<ChangesBrowserNode.Tag, Set<Change>> = emptyMap()

  val changes get() = stashedChanges

  private var currentStash: StashInfo? = null
  private var currentChangesFuture: CompletableFuture<GitStashCache.StashData>? = null

  var diffPreviewProcessor: GitStashDiffPreview? = null
    private set
  var editorTabPreview: EditorTabPreview? = null
    private set

  init {
    init()
    viewer.emptyText.text = GitBundle.message("stash.changes.empty")
    hideViewerBorder()

    Disposer.register(parentDisposable, this)
  }

  fun selectStash(stash: StashInfo?) {
    if (stash == currentStash) return
    currentStash = stash
    currentChangesFuture?.cancel(false)
    currentChangesFuture = null

    if (stash == null) {
      setEmpty { statusText -> statusText.text = GitBundle.message("stash.changes.empty") }
      return
    }

    setEmpty { statusText -> statusText.text = GitBundle.message("stash.changes.loading") }

    val futureChanges = stashCache.loadStashData(stash) ?: return
    currentChangesFuture = futureChanges
    futureChanges.thenRunAsync(Runnable {
      if (currentStash != stash) return@Runnable

      when (val stashData = currentChangesFuture?.get()) {
        is GitStashCache.StashData.Changes -> {
          setData(stashData.changes, stashData.parentCommits)
        }
        is GitStashCache.StashData.Error -> {
          setEmpty { statusText -> statusText.setText(stashData.error.localizedMessage, SimpleTextAttributes.ERROR_ATTRIBUTES) }
        }
      }
      currentChangesFuture = null
    }, EdtExecutorService.getInstance())
  }

  override fun createPopupMenuActions(): List<AnAction> {
    return super.createPopupMenuActions() + ActionManager.getInstance().getAction("Git.Stash.ChangesBrowser.ContextMenu")
  }

  override fun buildTreeModel(): DefaultTreeModel {
    val builder = TreeModelBuilder(myProject, grouping)
    builder.setChanges(stashedChanges, null)
    for ((tag, changes) in otherChanges) {
      if (changes.isEmpty()) continue
      builder.insertChanges(changes, builder.createTagNode(tag, SimpleTextAttributes.REGULAR_ATTRIBUTES, false))
    }
    return builder.build()
  }

  private fun setEmpty(updateEmptyText: (StatusText) -> Unit) = setData(emptyList(), emptyList(), updateEmptyText)

  private fun setData(stash: Collection<Change>, parents: Collection<GitCommit>) {
    setData(stash, parents) { statusText -> statusText.text = "" }
  }

  private fun setData(stash: Collection<Change>,
                      parents: Collection<GitCommit>,
                      updateEmptyText: (StatusText) -> Unit) {
    stashedChanges = stash
    otherChanges = parents.associate { parent ->
      val tag = MyTag(StringUtil.capitalize(parent.subject.substringBefore(":")), parent.id)
      Pair(tag, ReferenceOpenHashSet(parent.changes))
    }
    updateEmptyText(viewer.emptyText)
    viewer.rebuildTree()
  }

  override fun getShowDiffActionPreview(): DiffPreview? {
    return editorTabPreview
  }

  public override fun getDiffRequestProducer(userObject: Any): ChangeDiffRequestChain.Producer? {
    if (userObject !is Change) return null
    return ChangeDiffRequestProducer.create(myProject, userObject, prepareChangeContext(userObject))
  }

  fun getDiffWithLocalRequestProducer(userObject: Any): ChangeDiffRequestChain.Producer? {
    if (userObject !is Change) return null
    val changeWithLocal = ShowDiffWithLocalAction.getChangeWithLocal(userObject, false) ?: return null
    return ChangeDiffRequestProducer.create(myProject, changeWithLocal, prepareChangeContext(userObject))
  }

  private fun prepareChangeContext(userObject: Change): Map<Key<*>, Any> {
    val context = mutableMapOf<Key<*>, Any>()
    getTag(userObject)?.let { context[ChangeDiffRequestProducer.TAG_KEY] = it }
    return context
  }

  private fun getTag(change: Change): ChangesBrowserNode.Tag? {
    return otherChanges.asSequence().firstOrNull { it.value.contains(change) }?.key
  }

  fun setDiffPreviewInEditor(isInEditor: Boolean): GitStashDiffPreview {
    if (diffPreviewProcessor != null) Disposer.dispose(diffPreviewProcessor!!)
    val newProcessor = object: GitStashDiffPreview(myProject, viewer, isInEditor, this) {
      override fun getTag(change: Change) = this@GitStashChangesBrowser.getTag(change)
    }
    diffPreviewProcessor = newProcessor

    if (isInEditor) {
      editorTabPreview = object : GitStashEditorDiffPreview(newProcessor, viewer, this@GitStashChangesBrowser, focusMainUi) {
        override fun getCurrentName(): String {
          return changeViewProcessor.currentChangeName?.let { changeName ->
            val stashId = currentStash?.stash?.capitalize() ?: GitBundle.message("stash.editor.diff.preview.empty.title")
            GitBundle.message("stash.editor.diff.preview.id.change.title", stashId, changeName)
          } ?: GitBundle.message("stash.editor.diff.preview.empty.title")
        }
      }
    }
    else {
      editorTabPreview = null
    }

    return newProcessor
  }

  override fun dispose() {
  }

  private data class MyTag(@Nls private val text: String, private val hash: Hash): ChangesBrowserNode.Tag {
    override fun toString(): String = text
  }
}