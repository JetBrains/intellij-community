// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.savedPatches

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.shelf.*
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangesViewManager.SHELVED_CHANGELIST_KEY
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNodeRenderer
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.text.DateFormatUtil
import java.util.concurrent.CompletableFuture
import javax.swing.event.ChangeListener

class ShelfProvider(private val project: Project) : SavedPatchesProvider<ShelvedChangeList> {
  private val shelveManager: ShelveChangesManager get() = ShelveChangesManager.getInstance(project)

  override val dataClass: Class<ShelvedChangeList> get() = ShelvedChangeList::class.java
  override val dataKey: DataKey<List<ShelvedChangeList>> get() = SHELVED_CHANGELIST_KEY
  override val applyAction: AnAction get() = ActionManager.getInstance().getAction("Vcs.Shelf.Apply")
  override val popAction: AnAction get() = ActionManager.getInstance().getAction("Vcs.Shelf.Pop")

  override fun subscribeToPatchesListChanges(disposable: Disposable, listener: () -> Unit) {
    val disposableFlag = Disposer.newCheckedDisposable()
    Disposer.register(disposable, disposableFlag)
    project.messageBus.connect(disposable).subscribe(ShelveChangesManager.SHELF_TOPIC, ChangeListener {
      if (ApplicationManager.getApplication().isDispatchThread) {
        listener()
      }
      else {
        ApplicationManager.getApplication().invokeLater(listener) { disposableFlag.isDisposed }
      }
    })
  }

  override fun buildPatchesTree(modelBuilder: TreeModelBuilder) {
    val shelvesList = shelveManager.allLists.sortedByDescending { it.DATE }

    val shelvesRoot = SavedPatchesTree.TagWithCounterChangesBrowserNode(VcsBundle.message("shelf.root.node.title"))
    modelBuilder.insertSubtreeRoot(shelvesRoot)
    for (shelve in shelvesList.filter { l -> !l.isDeleted && !l.isRecycled }) {
      modelBuilder.insertSubtreeRoot(ShelvedChangeListChangesBrowserNode(ShelfObject(shelve)), shelvesRoot)
    }
  }

  class ShelvedChangeListChangesBrowserNode(private val shelf: ShelfObject) : ChangesBrowserNode<ShelfObject>(shelf) {
    override fun render(renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
      val listName = shelf.data.DESCRIPTION.ifBlank { VcsBundle.message("changes.nodetitle.empty.changelist.name") }
      val attributes = if (shelf.data.isRecycled || shelf.data.isDeleted) {
        SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES
      }
      else {
        SimpleTextAttributes.REGULAR_ATTRIBUTES
      }
      renderer.appendTextWithIssueLinks(listName, attributes)

      renderer.toolTipText = VcsBundle.message("saved.patch.created.on.date.at.time.tooltip", VcsBundle.message("shelf.tooltip.title"),
                                               DateFormatUtil.formatDate(shelf.data.DATE),
                                               DateFormatUtil.formatTime(shelf.data.DATE))
    }

    override fun getTextPresentation(): String = shelf.toString()
  }

  inner class ShelfObject(override val data: ShelvedChangeList) : SavedPatchesProvider.PatchObject<ShelvedChangeList> {
    override fun loadChanges(): CompletableFuture<SavedPatchesProvider.LoadingResult>? {
      return CompletableFuture.supplyAsync {
        data.loadChangesIfNeededOrThrow(project)
        data.changes!!.map { MyShelvedWrapper(it, null, data) }
          .union(data.binaryFiles.map { MyShelvedWrapper(null, it, data) })
      }.thenApply<SavedPatchesProvider.LoadingResult> { shelvedChanges ->
        SavedPatchesProvider.LoadingResult.Changes(shelvedChanges)
      }.exceptionally { throwable ->
        when (throwable) {
          is VcsException -> SavedPatchesProvider.LoadingResult.Error(throwable)
          else -> SavedPatchesProvider.LoadingResult.Error(VcsException(throwable))
        }
      }
    }

    override fun getDiffPreviewTitle(changeName: String?): String {
      return changeName?.let { name ->
        VcsBundle.message("shelve.editor.diff.preview.title", name)
      } ?: VcsBundle.message("shelved.version.name")
    }
  }

  private class MyShelvedWrapper(shelvedChange: ShelvedChange?,
                                 binaryFile: ShelvedBinaryFile?,
                                 changeList: ShelvedChangeList) : ShelvedWrapper(shelvedChange, binaryFile, changeList) {
    override fun getTag(): ChangesBrowserNode.Tag? = null
  }
}