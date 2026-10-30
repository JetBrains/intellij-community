// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.tools.util.base.DiffViewerBase
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.delay
import org.jetbrains.annotations.ApiStatus
import kotlin.time.Duration.Companion.milliseconds

/**
 * The dim of a preview. It shows one dim at a time. A new dim replaces the previous one in the same event, so the editor never paints a
 * frame without a dim in between. A closed diff tab or input in the editor ends the dim on its own.
 */
@ApiStatus.Internal
class StructuralChangesPreviewDim(private val project: Project) {
  private var handle: Disposable? = null

  /** The owner of the dim, such as the hunk that the dim shows. Null without a dim. */
  var owner: Any? = null
    private set

  /** True while the dim shows. */
  val isShown: Boolean
    get() = handle != null

  /**
   * Waits until the diff tab of [diffFile] shows [file] and finishes its diff, and then dims the viewer outside [lines]. A null [file] takes
   * the file that the tab shows. The wait stops when [isCurrent] gives false. Returns true when the new dim replaced the previous one.
   */
  @RequiresEdt
  suspend fun showInDiff(
    diffFile: VirtualFile,
    file: VirtualFile?,
    lines: ChangesBrowserStructuralNode.DiffLines,
    owner: Any?,
    isCurrent: () -> Boolean,
  ): Boolean {
    val viewer = awaitViewer(diffFile, file, isCurrent) ?: return false
    return replace(owner) { it.highlightDiff(viewer, lines) }
  }

  /**
   * Ends the previous dim and starts the dim that [create] gives. The new dim starts after the end of the previous one, because both can
   * wrap the same divider painter. Returns true when [create] gives a dim.
   */
  @RequiresEdt
  fun replace(owner: Any?, create: (StructuralChangesPreviewHighlighter) -> Disposable?): Boolean {
    clear()
    val handle = highlighter()?.let(create) ?: return false
    keep(handle, owner)
    return true
  }

  @RequiresEdt
  fun clear() {
    val previous = handle
    handle = null
    owner = null
    previous?.let { Disposer.dispose(it) }
  }

  /** Keeps the dim until it ends. A closed diff tab or input in the editor ends it, and then the preview counts as closed. */
  private fun keep(handle: Disposable, owner: Any?) {
    this.handle = handle
    this.owner = owner
    Disposer.register(handle) {
      if (this.handle === handle) {
        this.handle = null
        this.owner = null
      }
    }
  }

  private suspend fun awaitViewer(diffFile: VirtualFile, file: VirtualFile?, isCurrent: () -> Boolean): DiffViewerBase? {
    val highlighter = highlighter() ?: return null
    repeat(VIEWER_WAIT_ATTEMPTS) {
      if (!isCurrent()) return null
      val viewer = highlighter.findDiffViewer(diffFile)
      if (viewer != null && !viewer.hasPendingRediff() && (file == null || viewer.shows(file))) return viewer
      delay(VIEWER_WAIT_STEP)
    }
    return null
  }

  private fun DiffViewerBase.shows(file: VirtualFile): Boolean = request.contents.any { (it as? DocumentContent)?.highlightFile == file }

  /** Only the frontend module registers the highlighter. A process without that module shows the preview without the dim. */
  private fun highlighter(): StructuralChangesPreviewHighlighter? = project.serviceOrNull()

  private companion object {
    const val VIEWER_WAIT_ATTEMPTS = 100
    val VIEWER_WAIT_STEP = 50.milliseconds
  }
}
