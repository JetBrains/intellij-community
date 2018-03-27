/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.vcs.log.graph.impl

import com.intellij.vcs.log.graph.BaseTestGraphBuilder
import com.intellij.vcs.log.graph.BaseTestGraphBuilder.SimpleEdge
import com.intellij.vcs.log.graph.api.EdgeFilter
import com.intellij.vcs.log.graph.api.elements.GraphEdgeType
import com.intellij.vcs.log.graph.asString
import com.intellij.vcs.log.graph.collapsing.EdgeStorage
import com.intellij.vcs.log.graph.collapsing.EdgeStorageWrapper
import com.intellij.vcs.log.graph.utils.sortR
import org.junit.Assert.assertEquals
import org.junit.Test

class EdgeStorageTest : BaseTestGraphBuilder {
  private val nodeIdByIndex: (Int) -> Int = { it - 10 }
  private val nodeIndexById: (Int) -> Int = { it + 10 }

  fun create() = EdgeStorage()

  infix fun Int.to(edge: SimpleEdge) = FullEdge(this, edge.toNode, edge.type)
  infix fun Int.to(node: Int) = to(node.u)

  class FullEdge(val mainId: Int, val additionId: Int?, val edgeType: GraphEdgeType)

  operator fun EdgeStorage.plus(edge: FullEdge): EdgeStorage {
    createEdge(edge.mainId, edge.additionId ?: EdgeStorage.NULL_ID, edge.edgeType)
    return this
  }

  fun EdgeStorage.remove(edge: FullEdge): EdgeStorage {
    removeEdge(edge.mainId, edge.additionId ?: EdgeStorage.NULL_ID, edge.edgeType)
    return this
  }

  infix fun EdgeStorage.assert(s: String) = assertEquals(s, asString())

  fun EdgeStorage.asString(): String = knownIds.sortR().map {
    adapter.getAdjacentEdges(nodeIndexById(it), EdgeFilter.ALL).map { it.asString() }.joinToString(",")
  }.joinToString("|-")

  val EdgeStorage.adapter: EdgeStorageWrapper get() = EdgeStorageWrapper(this, nodeIndexById, nodeIdByIndex)

  @Test
  fun simple() = create() + (1 to 2) assert "11:12:n_U|-11:12:n_U"

  @Test
  fun dotted() = create() + (1 to 3.dot) assert "11:13:n_D|-11:13:n_D"

  @Test
  fun not_load() = create() + (1 to (-3).not_load) assert "11:n:-3_N"

  @Test
  fun not_load_null() = create() + (1 to null.not_load) assert "11:n:n_N"

  @Test
  fun down_dot() = create() + (1 to 3.down_dot) assert "11:n:3_O"

  @Test
  fun up_dot() = create() + (1 to 3.up_dot) assert "n:11:3_P"

  @Test
  fun negativeId() = create() + (-2 to -3) assert "7:8:n_U|-7:8:n_U"

  @Test
  fun bigId() = create() + (0x7fffff0 to -0x7fffff0) assert "-134217702:134217722:n_U|--134217702:134217722:n_U"

  @Test
  fun twoEqualEdge() = create() + (1 to 2) + (1 to 2) assert "11:12:n_U|-11:12:n_U"

  @Test
  fun twoFromOneNode() = create() + (1 to 2) + (1 to 3) assert "11:12:n_U,11:13:n_U|-11:12:n_U|-11:13:n_U"

}