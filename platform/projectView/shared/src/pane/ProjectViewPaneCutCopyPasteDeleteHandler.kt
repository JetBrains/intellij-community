// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.pane

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface ProjectViewPaneCutCopyPasteDeleteHandler {
  /**
   * Puts the given nodes into the clipboard, to be copied by a subsequent [performPaste].
   *
   * Called when the frontend performs Copy. The frontend only knows the node IDs: everything that
   * needs PSI (including whether the nodes can be copied at all) happens here.
   */
  suspend fun performCopy(nodeIds: List<Long>)

  /** The Cut counterpart of [performCopy]. */
  suspend fun performCut(nodeIds: List<Long>)

  /**
   * Pastes whatever [performCopy] or [performCut] put into the clipboard into the given nodes,
   * which is the current selection (normally a single node) used as the paste target.
   */
  suspend fun performPaste(nodeIds: List<Long>)

  /** Deletes the given nodes, which is the current selection. */
  suspend fun performDelete(nodeIds: List<Long>)
}
