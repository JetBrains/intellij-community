// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.impl.print

import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo
import com.intellij.vcs.log.graph.api.printer.GraphColorGetter
import com.intellij.vcs.log.graph.api.printer.GraphColorGetterFactory

private class GraphColorGetterByNode<CommitId>(private val permanentGraphInfo: PermanentGraphInfo<CommitId>,
                                               private val colorGetter: (CommitId, Int) -> Int) : GraphColorGetter {
  override fun getNodeColor(nodeId: Int, layoutIndex: Int): Int {
    return colorGetter(permanentGraphInfo.permanentCommitsInfo.getCommitId(nodeId), layoutIndex)
  }
}

/**
 * A factory for a simple [GraphColorGetter] implementation which allows to define the color for each node individually.
 * @param colorGetter a function which returns an integer representing a color for a provided commit id and layout index of the commit
 */
class GraphColorGetterByNodeFactory<CommitId>(private val colorGetter: (CommitId, Int) -> Int) : GraphColorGetterFactory<CommitId> {
  override fun createColorGetter(permanentGraphInfo: PermanentGraphInfo<CommitId>): GraphColorGetter {
    return GraphColorGetterByNode(permanentGraphInfo, colorGetter)
  }
}