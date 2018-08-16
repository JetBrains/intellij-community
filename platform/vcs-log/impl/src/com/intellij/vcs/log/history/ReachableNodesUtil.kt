// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.history

import com.intellij.openapi.util.Ref
import com.intellij.vcs.log.data.DataPack
import com.intellij.vcs.log.graph.api.LinearGraph
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo
import com.intellij.vcs.log.graph.impl.facade.ReachableNodes
import com.intellij.vcs.log.graph.impl.facade.VisibleGraphImpl
import com.intellij.vcs.log.graph.utils.LinearGraphUtils
import com.intellij.vcs.log.visible.VisiblePack

fun findMatchingAncestorNodeId(commitId: Int, permanentGraphInfo: PermanentGraphInfo<Int>, condition: (Int) -> Boolean): Int? {
  val resultNodeId = Ref<Int>()

  val startNodeId = permanentGraphInfo.permanentCommitsInfo.getNodeId(commitId)
  val reachableNodes = ReachableNodes(LinearGraphUtils.asLiteLinearGraph(permanentGraphInfo.linearGraph))
  reachableNodes.walk(setOf(startNodeId), true) { currentNodeId ->
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

fun findVisibleAncestorRow(commitId: Int, visiblePack: VisiblePack): Int? {
  val dataPack = visiblePack.dataPack
  val visibleGraph = visiblePack.visibleGraph
  if (dataPack is DataPack && dataPack.permanentGraph is PermanentGraphInfo<*> && visibleGraph is VisibleGraphImpl) {
    return findVisibleAncestorRow(commitId, visibleGraph.linearGraph, dataPack.permanentGraph as PermanentGraphInfo<Int>) { _ -> true }
  }
  return null
}

fun findVisibleAncestorRow(commitId: Int,
                           visibleLinearGraph: LinearGraph,
                           permanentGraphInfo: PermanentGraphInfo<Int>,
                           condition: (Int) -> Boolean): Int? {
  val nodeId = findMatchingAncestorNodeId(commitId, permanentGraphInfo) { nodeId ->
    condition(nodeId) && visibleLinearGraph.getNodeIndex(nodeId) != null
  } ?: return null
  return visibleLinearGraph.getNodeIndex(nodeId)
}