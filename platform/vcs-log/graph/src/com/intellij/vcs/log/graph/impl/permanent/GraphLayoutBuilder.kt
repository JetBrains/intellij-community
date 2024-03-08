// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.impl.permanent

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.vcs.log.graph.api.LinearGraph
import com.intellij.vcs.log.graph.utils.Dfs
import com.intellij.vcs.log.graph.utils.LinearGraphUtils
import com.intellij.vcs.log.graph.utils.walk
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntComparator
import it.unimi.dsi.fastutil.ints.IntList

object GraphLayoutBuilder {
  private val LOG = Logger.getInstance(GraphLayoutBuilder::class.java)

  @JvmStatic
  fun build(graph: LinearGraph, comparator: IntComparator): GraphLayoutImpl {
    val heads = getSortedHeads(graph, comparator)

    val layoutIndex = IntArray(graph.nodesCount())
    val startLayoutIndexForHead = IntArray(heads.size)
    var currentLayoutIndex = 1
    for (i in heads.indices) {
      startLayoutIndexForHead[i] = currentLayoutIndex
      currentLayoutIndex = dfs(graph, heads.getInt(i), currentLayoutIndex, layoutIndex)
    }
    return GraphLayoutImpl(layoutIndex, heads, startLayoutIndexForHead)
  }

  private fun getSortedHeads(graph: LinearGraph, comparator: IntComparator): IntList {
    val heads: IntList = IntArrayList()
    for (i in 0 until graph.nodesCount()) {
      if (LinearGraphUtils.getUpNodes(graph, i).isEmpty()) {
        heads.add(i)
      }
    }
    try {
      heads.sort(comparator)
    }
    catch (pce: ProcessCanceledException) {
      throw pce
    }
    catch (e: Exception) {
      // protection against possible comparator flaws
      LOG.error(e)
    }
    return heads
  }

  private fun dfs(graph: LinearGraph, startNodeIndex: Int, startLayoutIndex: Int, layoutIndex: IntArray): Int {
    var currentLayoutIndex = startLayoutIndex

    walk(startNodeIndex) { currentNode: Int ->
      val firstVisit = layoutIndex[currentNode] == 0
      if (firstVisit) layoutIndex[currentNode] = currentLayoutIndex

      val childWithoutLayoutIndex = LinearGraphUtils.getDownNodes(graph, currentNode).firstOrNull { layoutIndex[it] == 0 }
      if (childWithoutLayoutIndex == null) {
        if (firstVisit) currentLayoutIndex++
        return@walk Dfs.NextNode.NODE_NOT_FOUND
      }
      return@walk childWithoutLayoutIndex
    }

    return currentLayoutIndex
  }
}