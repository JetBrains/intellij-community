// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.merge

import com.intellij.diff.DiffManagerEx
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
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class MergeConflictIterativeDataHolder(
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
  fun getResolvedFiles(): Set<VirtualFile> {
    return mergeConflictModels.filter { it.value.getUnresolvedChanges().isEmpty() }.keys
  }

  suspend fun prepareModelIfSupported(file: VirtualFile, request: MergeRequest): MergeConflictModel? =
    withContext(Dispatchers.EDT) {
      if (request !is TextMergeRequest || !isMergeRequestSupported(request)) return@withContext null
      val model = mergeConflictModels.getOrPut(file) {
        val conflictResolver = LangSpecificMergeConflictResolverWrapper(project, request.contents)
        val settings = service<TextDiffSettingsHolder>().getSettings(DiffPlaces.MERGE)
        MergeConflictModel(project, request, conflictResolver).apply {
          rediff(settings.ignorePolicy, settings.isAutoResolveImportConflicts)
        }
      }
      IterativeResolveSupport.setData(request, model)
      model
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