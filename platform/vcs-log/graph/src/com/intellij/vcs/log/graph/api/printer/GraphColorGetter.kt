// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.api.printer

import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo

/**
 * Generates color ids for graph nodes.
 */
interface GraphColorGetter {
  /**
   * Returns color id for the provided [nodeId] and [layoutIndex].
   * @param nodeId      identifier of the node in the [com.intellij.vcs.log.graph.api.permanent.PermanentCommitsInfo].
   * @param layoutIndex identifier of the fragment this node belongs to.
   *
   * @see com.intellij.vcs.log.graph.api.permanent.PermanentCommitsInfo.getNodeId
   * @see com.intellij.vcs.log.graph.api.GraphLayout.getLayoutIndex
   */
  fun getNodeColor(nodeId: Int, layoutIndex: Int): Int
}

/**
 * A factory for [GraphColorGetter].
 */
interface GraphColorGetterFactory<CommitId> {
  /**
   * Creates an instance of [GraphColorGetter].
   * @param permanentGraphInfo a corresponding [PermanentGraphInfo] to use for node id to commit id conversion, getting layout indexes, etc.
   */
  fun createColorGetter(permanentGraphInfo: PermanentGraphInfo<CommitId>): GraphColorGetter
}
