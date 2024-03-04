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

class GraphLayoutBuilder private constructor(private val graph: LinearGraph, private val headNodeIndex: IntList) {
  private val layoutIndex = IntArray(graph.nodesCount())
  private val startLayoutIndexForHead = IntArray(headNodeIndex.size)
  private var currentLayoutIndex = 1

  private fun dfs(nodeIndex: Int) {
    walk(nodeIndex) { currentNode: Int ->
      val firstVisit = layoutIndex[currentNode] == 0
      if (firstVisit) layoutIndex[currentNode] = currentLayoutIndex

      var childWithoutLayoutIndex = -1
      for (childNodeIndex in LinearGraphUtils.getDownNodes(graph, currentNode)) {
        if (layoutIndex[childNodeIndex] == 0) {
          childWithoutLayoutIndex = childNodeIndex
          break
        }
      }
      if (childWithoutLayoutIndex == -1) {
        if (firstVisit) currentLayoutIndex++

        return@walk Dfs.NextNode.NODE_NOT_FOUND
      }
      else {
        return@walk childWithoutLayoutIndex
      }
    }
  }

  private fun build(): GraphLayoutImpl {
    for (i in headNodeIndex.indices) {
      val headNodeIndex = headNodeIndex.getInt(i)
      startLayoutIndexForHead[i] = currentLayoutIndex

      dfs(headNodeIndex)
    }

    return GraphLayoutImpl(layoutIndex, headNodeIndex, startLayoutIndexForHead)
  }

  companion object {
    private val LOG = Logger.getInstance(GraphLayoutBuilder::class.java)

    @JvmStatic
    fun build(graph: LinearGraph, headNodeIndexComparator: IntComparator): GraphLayoutImpl {
      val heads: IntList = IntArrayList()
      for (i in 0 until graph.nodesCount()) {
        if (LinearGraphUtils.getUpNodes(graph, i).isEmpty()) {
          heads.add(i)
        }
      }
      try {
        heads.sort(headNodeIndexComparator)
      }
      catch (pce: ProcessCanceledException) {
        throw pce
      }
      catch (e: Exception) {
        // protection against possible comparator flaws
        LOG.error(e)
      }
      val builder = GraphLayoutBuilder(graph, heads)
      return builder.build()
    }
  }
}
