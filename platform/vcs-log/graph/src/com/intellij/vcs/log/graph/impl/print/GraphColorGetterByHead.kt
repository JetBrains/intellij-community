// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.impl.print

import com.intellij.vcs.log.graph.GraphColorManager
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo
import com.intellij.vcs.log.graph.api.printer.GraphColorGetterFactory
import com.intellij.vcs.log.graph.api.printer.GraphColorGetter

private class GraphColorGetterByHead<CommitId>(private val permanentGraphInfo: PermanentGraphInfo<CommitId>,
                                               private val colorManager: GraphColorManager<CommitId>) : GraphColorGetter {
  override fun getNodeColor(nodeId: Int, layoutIndex: Int): Int {
    val headNodeId = if (nodeId < 0) 0 else permanentGraphInfo.permanentGraphLayout.getOneOfHeadNodeIndex(nodeId)
    val headCommitId = permanentGraphInfo.permanentCommitsInfo.getCommitId(headNodeId)
    val headLayoutIndex = permanentGraphInfo.permanentGraphLayout.getLayoutIndex(headNodeId)
    return colorManager.getColor(headCommitId, headLayoutIndex, layoutIndex)
  }
}

/**
 * A factory for [GraphColorGetter] which allows to define color for main branches and fragments of the graph, rather than for individual nodes.
 * @property colorManager a [GraphColorManager] implementation to get colors by head commit of the main branch
 *                        and by head commit and layout index of the fragment.
 */
class GraphColorGetterByHeadFactory<CommitId>(private val colorManager: GraphColorManager<CommitId>) : GraphColorGetterFactory<CommitId> {
  override fun createColorGetter(permanentGraphInfo: PermanentGraphInfo<CommitId>): GraphColorGetter {
    return GraphColorGetterByHead(permanentGraphInfo, colorManager)
  }
}