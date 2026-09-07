// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.merge

import com.intellij.diff.DiffManagerEx
import com.intellij.diff.InvalidDiffRequestException
import com.intellij.diff.comparison.DiffTooBigException
import com.intellij.diff.merge.IterativeResolveSupport
import com.intellij.diff.merge.LangSpecificMergeConflictResolverWrapper
import com.intellij.diff.merge.MergeConflictModel
import com.intellij.diff.merge.MergeRequest
import com.intellij.diff.merge.MergeRequestHandler
import com.intellij.diff.merge.TextMergeRequest
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder
import com.intellij.diff.util.DiffPlaces
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.components.service
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import it.unimi.dsi.fastutil.ints.IntArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class MergeConflictIterativeDataHolder(
  private val project: Project?,
  parentDisposable: Disposable,
) : Disposable {
  private val mergeConflictModels = mutableMapOf<VirtualFile, MergeConflictModel>()

  init {
    Disposer.register(parentDisposable, this)
  }

  @RequiresEdt
  fun getMergeConflictModel(file: VirtualFile): MergeConflictModel? = mergeConflictModels[file]

  @RequiresEdt
  fun isFileResolved(file: VirtualFile): Boolean {
    val mergeConflictModel = mergeConflictModels[file] ?: return false
    return mergeConflictModel.getAllChanges().all { change -> change.isResolved }
  }

  @RequiresEdt
  fun isFileReviewed(file: VirtualFile): Boolean {
    val mergeConflictModel = mergeConflictModels[file] ?: return false
    return mergeConflictModel.wasReviewed
  }

  @RequiresEdt
  fun getResolvedFilesAndModels(): Map<VirtualFile, MergeConflictModel> {
    return mergeConflictModels.filter { it.value.getUnresolvedChanges().isEmpty() }
  }

  @RequiresEdt
  fun removeFiles(files: List<VirtualFile>) {
    val removedModels = files.mapNotNull { mergeConflictModels.remove(it) }
    runWriteAction {
      removedModels.forEach { model ->
        val affected = model.getAllChanges().mapTo(IntArrayList()) { it.index }
        model.executeMergeCommand(commandName = DiffBundle.message("merge.dialog.reset.change.command"),
                                  commandGroupId = null,
                                  undoConfirmationPolicy = UndoConfirmationPolicy.DEFAULT,
                                  bulkUpdate = true,
                                  affectedIndexes = affected) {
          model.resetAllChanges()
        }
      }
    }
    removedModels.forEach(Disposer::dispose)
  }

  /**
   * Prepares the iterative resolution model for the [file] from an already built [request].
   * Returns `null` when an external tool or a non-text request handles the file.
   *
   * @throws DiffTooBigException when the diff is too big to compute. It is a `ProcessCanceledException`, so the
   *   caller must let it propagate.
   * @throws InvalidDiffRequestException when the model cannot write the output.
   */
  @Throws(DiffTooBigException::class, InvalidDiffRequestException::class)
  suspend fun prepareModelIfSupported(file: VirtualFile, request: MergeRequest): MergeConflictModel? =
    withContext(Dispatchers.EDT) {
      if (request !is TextMergeRequest || !isMergeRequestSupported(request)) return@withContext null
      val model = mergeConflictModels.getOrPut(file) {
        val conflictResolver = LangSpecificMergeConflictResolverWrapper(project, request.contents)
        val settings = service<TextDiffSettingsHolder>().getSettings(DiffPlaces.MERGE)
        val created = MergeConflictModel(project, request, conflictResolver)
        var initialized = false
        try {
          created.rediff(settings.ignorePolicy, settings.isAutoResolveImportConflicts)
          initialized = true
          created
        }
        finally {
          // The model registers a document listener in its constructor. If rediff fails (for example with
          // DiffTooBigException), dispose it, or it stays a Disposer root and pins the project.
          if (!initialized) Disposer.dispose(created)
        }
      }
      IterativeResolveSupport.setData(request, model)
      model
    }

  @RequiresEdt
  fun getAiFileSnapshot(file: VirtualFile): MergeConflictAiFileSnapshot? {
    val model = mergeConflictModels[file] ?: return null
    val changes = model.getAllChanges()
    return MergeConflictAiFileSnapshot(
      totalConflicts = changes.size,
      resolvedConflicts = changes.count { it.isResolved },
      unresolvedConflicts = changes.count { !it.isResolved },
    )
  }

  @RequiresEdt
  fun resolveAutoResolvableConflicts(file: VirtualFile): Boolean {
    val model = mergeConflictModels[file] ?: return false
    if (model.getAutoResolvableChanges().isEmpty()) return false

    runWriteAction {
      model.resolveAllChangesAutomatically()
    }
    return true
  }

  @RequiresEdt
  override fun dispose() {
    mergeConflictModels.values.forEach {
      Disposer.dispose(it)
    }
    mergeConflictModels.clear()
  }

  private fun isMergeRequestSupported(mergeRequest: TextMergeRequest): Boolean {
    return when (DiffManagerEx.getInstance().getHandler(project, mergeRequest)) {
      is MergeRequestHandler.BuiltInHandler -> true
      is MergeRequestHandler.UserConfiguredExternalToolHandler, is MergeRequestHandler.ExtensionBasedHandler -> false
    }
  }
}

@ApiStatus.Internal
data class MergeConflictAiFileSnapshot(
  @JvmField val totalConflicts: Int,
  @JvmField val resolvedConflicts: Int,
  @JvmField val unresolvedConflicts: Int,
)