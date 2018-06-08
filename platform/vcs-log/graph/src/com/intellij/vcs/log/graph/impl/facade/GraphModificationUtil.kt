// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.graph.impl.facade

import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo
import com.intellij.vcs.log.graph.collapsing.DottedFilterEdgesGenerator

fun <CommitId> hideInplace(graphController: LinearGraphController,
                           permanentGraphInfo: PermanentGraphInfo<CommitId>,
                           toHide: Set<CommitId>): Boolean {
  if (graphController is CascadeController) {
    val result = graphController.performAction action@{ cc ->
      if (cc is FilteredController) {
        val rowsToHide = permanentGraphInfo.permanentCommitsInfo.convertToNodeIds(toHide).mapNotNullTo(mutableSetOf()) {
          cc.compiledGraph.getNodeIndex(it)
        }
        DottedFilterEdgesGenerator.hideInplace(cc.collapsedGraph, rowsToHide)
        return@action GraphChangesUtil.SOME_CHANGES
      }
      return@action null
    }
    return result != null
  }
  return false
}