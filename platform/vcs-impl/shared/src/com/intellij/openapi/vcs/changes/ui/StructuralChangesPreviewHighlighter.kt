// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.diff.tools.util.base.DiffViewerBase
import com.intellij.diff.util.LineRange
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface StructuralChangesPreviewHighlighter {
  @RequiresEdt
  fun highlight(file: VirtualFile, range: TextRange, modificationStamp: Long): Disposable?

  /** Returns the viewer of the diff editor tab that shows [diffFile], or null when no tab shows a viewer. */
  @RequiresEdt
  fun findDiffViewer(diffFile: VirtualFile): DiffViewerBase? = null

  /**
   * Dims [viewer] outside [lines] and brings the lines into view. The dim follows each rediff and goes away on input in the viewer. While
   * the dim shows, each editor of [viewer] names the lines of the hunk with [STRUCTURAL_DIFF_DIM_HUNK_LINES].
   */
  @RequiresEdt
  fun highlightDiff(viewer: DiffViewerBase, lines: ChangesBrowserStructuralNode.DiffLines): Disposable? = null
}

/**
 * The lines of the hunk on an editor of a diff, while the dim of [StructuralChangesPreviewHighlighter.highlightDiff] shows the hunk. The
 * dim covers the other lines of the editor. A side without lines of the hunk has an empty range at the place of the hunk.
 */
@ApiStatus.Internal
val STRUCTURAL_DIFF_DIM_HUNK_LINES: Key<LineRange> = Key.create("vcs.structural.diff.dim.hunk.lines")
