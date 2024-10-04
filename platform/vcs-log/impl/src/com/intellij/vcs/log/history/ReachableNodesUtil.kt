// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.history

import com.intellij.openapi.util.Ref
import com.intellij.vcs.log.data.DataPack
import com.intellij.vcs.log.graph.api.LinearGraph
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo
import com.intellij.vcs.log.graph.impl.facade.VisibleGraphImpl
import com.intellij.vcs.log.graph.utils.DfsWalk
import com.intellij.vcs.log.visible.VisiblePack

private fun LinearGraph.findAncestorNode(startNodeId: Int, condition: (Int) -> Boolean): Int? {
  val resultNodeId = Ref<Int>()

  DfsWalk(setOf(startNodeId), this).walk(true) { currentNodeId: Int ->
    if (condition(currentNodeId)) {
      resultNodeId.set(currentNodeId)
      false // stop walk, we have found it
    }
    else {
      true // continue walk
    }
  }

  return resultNodeId.get()
}

internal fun findVisibleAncestorRow(commitId: Int, visiblePack: VisiblePack): Int? {
  val dataPack = visiblePack.dataPack
  val visibleGraph = visiblePack.visibleGraph
  if (dataPack is DataPack && dataPack.permanentGraph is PermanentGraphInfo<*> && visibleGraph is VisibleGraphImpl) {
    return findVisibleAncestorRow(commitId, visibleGraph.linearGraph, dataPack.permanentGraph as PermanentGraphInfo<Int>) { true }
  }
  return null
}

internal fun findVisibleAncestorRow(commitId: Int,
                           visibleLinearGraph: LinearGraph,
                           permanentGraphInfo: PermanentGraphInfo<Int>,
                           condition: (Int) -> Boolean): Int? {
  val startNodeId = permanentGraphInfo.permanentCommitsInfo.getNodeId(commitId)
  val ancestorNodeId = permanentGraphInfo.linearGraph.findAncestorNode(startNodeId) { nodeId: Int ->
    condition(nodeId) && visibleLinearGraph.getNodeIndex(nodeId) != null
  } ?: return null
  return visibleLinearGraph.getNodeIndex(ancestorNodeId)
}