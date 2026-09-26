// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.savedPatches

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode.Tag
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.PresentableChange
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder
import org.jetbrains.annotations.Nls
import java.util.concurrent.CompletableFuture
import javax.swing.JComponent

interface SavedPatchesProvider<S> {
  val dataClass: Class<S>
  val tag: Tag

  val applyAction: AnAction
  val popAction: AnAction

  fun subscribeToPatchesListChanges(disposable: Disposable, listener: () -> Unit)
  fun isEmpty(): Boolean
  fun buildPatchesTree(modelBuilder: TreeModelBuilder, showRootNode: Boolean)

  fun uiDataSnapshot(sink: DataSink, selectedObjects: Iterable<PatchObject<*>>)

  interface PatchObject<S> {
    val data: S

    fun loadChanges(): CompletableFuture<LoadingResult>?
    fun cachedChanges(): Collection<ChangeObject>?
    fun getDiffPreviewTitle(changeName: String?): String
    fun getLabelComponent(tree: ChangesTree, row: Int, selected: Boolean): JComponent? = null
  }

  interface ChangeObject : PresentableChange {
    val originalFilePath: FilePath? get() = null

    fun createDiffRequestProducer(project: Project?): ChangeDiffRequestChain.Producer?
    fun createDiffWithLocalRequestProducer(project: Project?, useBeforeVersion: Boolean): ChangeDiffRequestChain.Producer?
    fun asChange(): Change? = null
  }

  sealed class LoadingResult {
    class Changes(val changes: Collection<ChangeObject>) : LoadingResult()
    class Error(val message: @Nls String) : LoadingResult() {
      constructor(error: Throwable) : this(error.localizedMessage)
    }
  }
}