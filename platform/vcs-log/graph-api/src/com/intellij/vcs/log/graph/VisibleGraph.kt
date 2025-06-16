// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph

import com.intellij.vcs.log.graph.actions.ActionController

/**
 * Row index used in [VisibleGraph]
 * Can be obtained by [VisibleGraph.getVisibleRowIndex] method
 */
typealias VcsLogVisibleGraphIndex = Int

/**
 * A part of [PermanentGraph] which should be drawn on screen (e.g. with applied filters). <br></br>
 * This is one per client (page), all access to VisibleGraph should be synchronized. <br></br>
 * It refers to the [PermanentGraph], but it occupies a little on its own.
 */
interface VisibleGraph<Id> {
  val visibleCommitCount: Int

  val recommendedWidth: Int

  val actionController: ActionController<Id>

  fun getRowInfo(visibleRow: VcsLogVisibleGraphIndex): RowInfo<Id>

  fun getVisibleRowIndex(id: Id): VcsLogVisibleGraphIndex?
}
