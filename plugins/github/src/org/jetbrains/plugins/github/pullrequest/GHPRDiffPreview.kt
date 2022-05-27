// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor.ChangeWrapper
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor.Wrapper
import com.intellij.openapi.vcs.changes.DiffPreview
import com.intellij.openapi.vcs.changes.actions.diff.CombinedDiffPreview
import com.intellij.openapi.vcs.changes.actions.diff.CombinedDiffPreviewModel
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.Processor
import org.jetbrains.plugins.github.pullrequest.action.GHPRShowDiffActionProvider
import org.jetbrains.plugins.github.pullrequest.data.GHPRFilesManager
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import java.util.*
import java.util.stream.Stream
import kotlin.streams.toList

internal class GHPRDiffPreview(private val prId: GHPRIdentifier?,
                               private val filesManager: GHPRFilesManager) : DiffPreview {
  val sourceId = UUID.randomUUID().toString()

  override fun updateDiffAction(event: AnActionEvent) {
    GHPRShowDiffActionProvider.updateAvailability(event)
  }

  override fun openPreview(requestFocus: Boolean): Boolean {
    if (prId == null) {
      filesManager.createAndOpenNewPRDiffPreviewFile(sourceId, requestFocus)
    }
    else {
      filesManager.createAndOpenDiffFile(prId, requestFocus)
    }
    return true
  }

  override fun closePreview() {
  }
}

internal class GHPRCombinedDiffPreview(private val prId: GHPRIdentifier?,
                                       private val filesManager: GHPRFilesManager,
                                       tree: ChangesTree) :
  GHPRCombinedDiffPreviewBase(prId, filesManager, tree, filesManager) {

  override fun doOpenPreview(requestFocus: Boolean): Boolean {
    val sourceId = tree.id
    if (prId == null) {
      filesManager.createAndOpenNewPRDiffPreviewFile(sourceId, requestFocus)
    }
    else {
      filesManager.createAndOpenDiffPreviewFile(prId, sourceId, requestFocus)
    }
    return true
  }
}

internal abstract class GHPRCombinedDiffPreviewBase(private val prId: GHPRIdentifier?,
                                                    private val filesManager: GHPRFilesManager,
                                                    tree: ChangesTree,
                                                    parentDisposable: Disposable) : CombinedDiffPreview(tree, parentDisposable) {

  override val previewFile: VirtualFile
    get() =
      if (prId == null) filesManager.createOrGetNewPRDiffFile(tree.id)
      else filesManager.createOrGetDiffFile(prId, tree.id)

  override fun createModel(): CombinedDiffPreviewModel = GHPRCombinedDiffPreviewModel(tree, parentDisposable)

  override fun getCombinedDiffTabTitle(): String = previewFile.name

  override fun updateDiffAction(event: AnActionEvent) {
    super.updateDiffAction(event)
    GHPRShowDiffActionProvider.updateAvailability(event)
  }

  override fun openPreview(requestFocus: Boolean): Boolean {
    if (!ensureHasContent()) return false
    return openPreviewEditor(requestFocus)
  }

  private fun ensureHasContent(): Boolean {
    updatePreviewProcessor.refresh(false)
    return hasContent()
  }

  private fun openPreviewEditor(requestFocus: Boolean): Boolean {
    escapeHandler?.let { handler -> registerEscapeHandler(previewFile, handler) }
    return doOpenPreview(requestFocus)
  }

  abstract fun doOpenPreview(requestFocus: Boolean): Boolean

  companion object {
    fun createAndSetupDiffPreview(tree: ChangesTree, prId: GHPRIdentifier?, filesManager: GHPRFilesManager): DiffPreview {
      val diffPreview =
        if (Registry.`is`("enable.combined.diff")) GHPRCombinedDiffPreview(prId, filesManager, tree)
        else GHPRDiffPreview(prId, filesManager)

      tree.apply {
        doubleClickHandler = Processor { e ->
          if (EditSourceOnDoubleClickHandler.isToggleEvent(this, e)) return@Processor false
          diffPreview.performDiffAction()
          true
        }
        enterKeyHandler = Processor {
          diffPreview.performDiffAction()
          true
        }
      }

      return diffPreview
    }
  }
}

private class GHPRCombinedDiffPreviewModel(tree: ChangesTree, parentDisposable: Disposable) :
  CombinedDiffPreviewModel(tree, prepareCombinedDiffModelRequests(tree.project, tree.allChanges), parentDisposable) {

  override fun getAllChanges(): Stream<out Wrapper> {
    return tree.allChangesStream
  }

  override fun getSelectedChanges(): Stream<out Wrapper> {
    return VcsTreeModelData.selected(tree).userObjectsStream(Change::class.java).map { change -> ChangeWrapper(change) }
  }

  companion object {
    private val ChangesTree.allChanges get() = allChangesStream.toList()

    private val ChangesTree.allChangesStream
      get() = VcsTreeModelData.all(this).userObjectsStream(Change::class.java).map { change -> ChangeWrapper(change) }
  }
}
