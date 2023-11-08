// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.savedPatches

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode.Tag
import org.jetbrains.annotations.Nls
import java.awt.Graphics2D
import java.util.concurrent.CompletableFuture
import java.util.stream.Stream

interface SavedPatchesProvider<S> {
  val dataClass: Class<S>
  val tag: Tag

  val applyAction: AnAction
  val popAction: AnAction

  fun subscribeToPatchesListChanges(disposable: Disposable, listener: () -> Unit)
  fun isEmpty(): Boolean
  fun buildPatchesTree(modelBuilder: TreeModelBuilder)
  fun getData(dataId: String, selectedObjects: Stream<PatchObject<*>>): Any?

  interface PatchObject<S> {
    val data: S

    fun loadChanges(): CompletableFuture<LoadingResult>?
    fun cachedChanges(): Collection<ChangeObject>?
    fun getDiffPreviewTitle(changeName: String?): String
    fun createPainter(tree: ChangesTree, renderer: ChangesTreeCellRenderer, row: Int, selected: Boolean): Painter? = null

    interface Painter {
      fun paint(graphics: Graphics2D)
    }
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