// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.impl.permanent

import com.intellij.vcs.log.graph.api.GraphLayout
import com.intellij.vcs.log.graph.utils.impl.CompressedIntList
import it.unimi.dsi.fastutil.ints.IntList
import org.jetbrains.annotations.ApiStatus
import java.util.*
import kotlin.math.max

@ApiStatus.Internal
class GraphLayoutImpl(layoutIndex: IntArray, private val headNodes: IntList) : GraphLayout {
  private val layoutIndexForHeadNodes = getLayoutIndexesForHeads(layoutIndex, headNodes)
  private val layoutIndex = CompressedIntList.newInstance(layoutIndex)

  override fun getLayoutIndex(nodeIndex: Int) = layoutIndex[nodeIndex]

  override fun getOneOfHeadNodeIndex(nodeIndex: Int) = getHeadNodeIndex(getLayoutIndex(nodeIndex))

  private fun getHeadNodeIndex(layoutIndex: Int) = headNodes.getInt(getHeadOrder(layoutIndex))

  override fun getHeadNodeIndex() = headNodes

  private fun getHeadOrder(layoutIndex: Int): Int {
    val i = Arrays.binarySearch(layoutIndexForHeadNodes, layoutIndex)
    return if (i < 0) max(0, (-i - 2)) else i
  }
}

private fun getLayoutIndexesForHeads(layoutIndexes: IntArray, headNodeIndexes: IntList): IntArray {
  val result = IntArray(headNodeIndexes.size)
  for (i in result.indices) {
    result[i] = layoutIndexes[headNodeIndexes.getInt(i)]
  }
  return result
}
