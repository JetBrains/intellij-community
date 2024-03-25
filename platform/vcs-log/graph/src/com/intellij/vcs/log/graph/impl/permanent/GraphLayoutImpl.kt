// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.impl.permanent

import com.intellij.vcs.log.graph.api.GraphLayout
import com.intellij.vcs.log.graph.utils.impl.CompressedIntList
import it.unimi.dsi.fastutil.ints.IntList
import java.util.*
import kotlin.math.max

class GraphLayoutImpl(layoutIndex: IntArray,
                      private val headNodeIndex: IntList,
                      private val startLayoutIndexForHead: IntArray) : GraphLayout {
  private val layoutIndex = CompressedIntList.newInstance(layoutIndex)

  override fun getLayoutIndex(nodeIndex: Int) = layoutIndex[nodeIndex]

  override fun getOneOfHeadNodeIndex(nodeIndex: Int) = getHeadNodeIndex(getLayoutIndex(nodeIndex))

  private fun getHeadNodeIndex(layoutIndex: Int) = headNodeIndex.getInt(getHeadOrder(layoutIndex))

  override fun getHeadNodeIndex() = headNodeIndex

  private fun getHeadOrder(layoutIndex: Int): Int {
    val i = Arrays.binarySearch(startLayoutIndexForHead, layoutIndex)
    return if (i < 0) max(0.0, (-i - 2).toDouble()).toInt() else i
  }
}
