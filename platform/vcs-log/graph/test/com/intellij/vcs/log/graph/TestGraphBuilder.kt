/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.vcs.log.graph

import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.HashMap
import com.intellij.util.containers.MultiMap
import com.intellij.vcs.log.graph.BaseTestGraphBuilder.SimpleEdge
import com.intellij.vcs.log.graph.BaseTestGraphBuilder.SimpleNode
import com.intellij.vcs.log.graph.api.EdgeFilter
import com.intellij.vcs.log.graph.api.LinearGraph
import com.intellij.vcs.log.graph.api.elements.GraphEdge
import com.intellij.vcs.log.graph.api.elements.GraphEdgeType
import com.intellij.vcs.log.graph.api.elements.GraphNode
import com.intellij.vcs.log.graph.api.elements.GraphNodeType
import com.intellij.vcs.log.graph.api.permanent.PermanentCommitsInfo
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo
import com.intellij.vcs.log.graph.impl.facade.LinearGraphController
import com.intellij.vcs.log.graph.impl.facade.VisibleGraphImpl
import com.intellij.vcs.log.graph.impl.permanent.GraphLayoutBuilder
import com.intellij.vcs.log.graph.impl.permanent.PermanentLinearGraphImpl
import com.intellij.vcs.log.graph.utils.LinearGraphUtils
import com.intellij.vcs.log.graph.utils.TimestampGetter
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

public interface BaseTestGraphBuilder {
  public val Int.U: SimpleNode get() = SimpleNode(this, GraphNodeType.USUAL)
  public val Int.UNM: SimpleNode get() = SimpleNode(this, GraphNodeType.UNMATCHED)
  public val Int.NOT_LOAD: SimpleNode get() = SimpleNode(this, GraphNodeType.NOT_LOAD_COMMIT)

  public val Int.u: SimpleEdge get() = SimpleEdge(this, GraphEdgeType.USUAL)
  public val Int.dot: SimpleEdge get() = SimpleEdge(this, GraphEdgeType.DOTTED)
  public val Int?.up_dot: SimpleEdge get() = SimpleEdge(this, GraphEdgeType.DOTTED_ARROW_UP)
  public val Int?.down_dot: SimpleEdge get() = SimpleEdge(this, GraphEdgeType.DOTTED_ARROW_DOWN)
  public val Int?.not_load: SimpleEdge get() = SimpleEdge(this, GraphEdgeType.NOT_LOAD_COMMIT)

  class SimpleEdge(val toNode: Int?, val type: GraphEdgeType = GraphEdgeType.USUAL)
  class SimpleNode(val nodeId: Int, val type: GraphNodeType = GraphNodeType.USUAL)
}

public class TestGraphBuilder : BaseTestGraphBuilder {
  private val nodes = ArrayList<NodeWithEdges>()

  public fun done(): LinearGraph = TestLinearGraph(nodes)

  public fun Int.invoke(): Unit = newNode(asSimpleNode())
  public fun Int.invoke(vararg edge: Int): Unit = newNode(asSimpleNode(), edge.asSimpleEdges())
  public fun Int.invoke(vararg edge: SimpleEdge): Unit = newNode(asSimpleNode(), edge.toList())
  public fun SimpleNode.invoke(): Unit = newNode(this)
  public fun SimpleNode.invoke(vararg edge: Int): Unit = newNode(this, edge.asSimpleEdges())
  public fun SimpleNode.invoke(vararg edge: SimpleEdge): Unit = newNode(this, edge.toList())

  private class NodeWithEdges(val nodeId: Int, val edges: List<SimpleEdge>, val type: GraphNodeType = GraphNodeType.USUAL)

  private fun IntArray.asSimpleEdges() = map { SimpleEdge(it) }
  private fun Int.asSimpleNode() = SimpleNode(this)

  private fun newNode(node: SimpleNode, edges: List<SimpleEdge> = listOf()) {
    nodes add NodeWithEdges(node.nodeId, edges, node.type)
  }

  fun node(id: Int, vararg edge: Int) {
    nodes add NodeWithEdges(id, edge.map {
      SimpleEdge(it, GraphEdgeType.USUAL)
    })
  }

  fun node(id: Int, vararg edge: SimpleEdge) {
    nodes add NodeWithEdges(id, edge.toList())
  }

  private class TestLinearGraph(buildNodes: List<NodeWithEdges>) : LinearGraph {
    private val nodes: List<GraphNode>
    private val nodeIndexToId: Map<Int, Int>
    private val nodeIdToIndex: Map<Int, Int>
    private val edges = MultiMap<Int, GraphEdge>()

    val SimpleEdge.toIndex: Int?  get() = toNode?.let { nodeIdToIndex[it] }

    init {
      val idsMap = HashMap<Int, Int>()
      nodes = buildNodes.map2 {index, it ->
        idsMap[index] = it.nodeId
        GraphNode(index, it.type)
      }
      nodeIndexToId = idsMap
      nodeIdToIndex = ContainerUtil.reverseMap(idsMap)

      // create edges
      for (node in buildNodes) {
        val nodeIndex = nodeIdToIndex[node.nodeId]!!
        for (simpleEdge in node.edges) {
          val edgeType = simpleEdge.type

          if (edgeType.isNormalEdge()) {
            val anotherNodeIndex = simpleEdge.toIndex
            assert(anotherNodeIndex != null, "Graph is incorrect. Node ${node.nodeId} has ${edgeType} edge to not existed node: ${simpleEdge.toNode}")

            val graphEdge = GraphEdge.createNormalEdge(anotherNodeIndex!!, nodeIndex, edgeType)
            edges.putValue(nodeIndex, graphEdge)
            edges.putValue(anotherNodeIndex, graphEdge)
          } else {
            edges.putValue(nodeIndex, GraphEdge.createEdgeWithTargetId(nodeIndex, simpleEdge.toNode, edgeType))
          }
        }
      }

    }

    override fun nodesCount() = nodes.size()

    override fun getNodeId(nodeIndex: Int): Int = nodeIndexToId[nodeIndex]!!

    override fun getAdjacentEdges(nodeIndex: Int, filter: EdgeFilter)
        = edges[nodeIndex].filter {
      if (it.getType().isNormalEdge()) {
        (LinearGraphUtils.isEdgeUp(it, nodeIndex) && filter.upNormal)
            || (LinearGraphUtils.isEdgeDown(it, nodeIndex) && filter.downNormal)
      } else {
        filter.special
      }
    }

    override fun getGraphNode(nodeIndex: Int) = nodes[nodeIndex]

    override fun getNodeIndex(nodeId: Int) = nodeIdToIndex[nodeId]

  }
}

private fun LinearGraph.assertEdge(nodeIndex: Int, edge: GraphEdge) {
  if (edge.getType().isNormalEdge()) {
    if (nodeIndex == edge.getUpNodeIndex()) {
      assertTrue(getAdjacentEdges(edge.getDownNodeIndex()!!, EdgeFilter.NORMAL_UP).contains(edge))
    } else {
      assertTrue(nodeIndex == edge.getDownNodeIndex())
      assertTrue(getAdjacentEdges(edge.getUpNodeIndex()!!, EdgeFilter.NORMAL_DOWN).contains(edge))
    }
  } else {
    when (edge.getType()) {
      GraphEdgeType.NOT_LOAD_COMMIT, GraphEdgeType.DOTTED_ARROW_DOWN -> {
        assertTrue(nodeIndex == edge.getUpNodeIndex())
        assertNull(edge.getDownNodeIndex())
      }
      GraphEdgeType.DOTTED_ARROW_UP -> {
        assertTrue(nodeIndex == edge.getDownNodeIndex())
        assertNull(edge.getUpNodeIndex())
      }
    }
  }
}

public fun LinearGraph.asTestGraphString(sorted: Boolean = false): String = StringBuilder {
  for (nodeIndex in 0..nodesCount() - 1) {
    val node = getGraphNode(nodeIndex)
    append(getNodeId(nodeIndex))
    assertEquals(nodeIndex, node.getNodeIndex(),
        "nodeIndex: $nodeIndex, but for node with this index(nodeId: ${getNodeId(nodeIndex)}) nodeIndex: ${node.getNodeIndex()}"
    )
    when (node.getType()) {
      GraphNodeType.UNMATCHED -> append(".UNM")
      GraphNodeType.NOT_LOAD_COMMIT -> append(".NOT_LOAD")
    }

    // edges
    var adjacentEdges = getAdjacentEdges(nodeIndex, EdgeFilter.ALL)
    if (sorted) {
      adjacentEdges = adjacentEdges.sortBy(GraphStrUtils.GRAPH_ELEMENT_COMPARATOR)
    }

    append("(")
    adjacentEdges.map {
      assertEdge(nodeIndex, it)
      if (it.getUpNodeIndex() == nodeIndex) {
        val startId = if (it.getType().isNormalEdge()) {
          getNodeId(it.getDownNodeIndex()!!).toString()
        } else if (it.getTargetId() != null) {
          it.getTargetId().toString()
        } else {
          "null"
        }

        when (it.getType()!!) {
          GraphEdgeType.USUAL -> startId
          GraphEdgeType.DOTTED -> "$startId.dot"
          GraphEdgeType.DOTTED_ARROW_UP -> "$startId.up_dot"
          GraphEdgeType.DOTTED_ARROW_DOWN -> "$startId.down_dot"
          GraphEdgeType.NOT_LOAD_COMMIT -> "$startId.not_load"
        }
      } else {
        null
      }
    }.mapNotNull { it }.joinTo(this, separator = ", ")

    append(")")
    append("\n")
  }
}.toString()

public fun graph(f: TestGraphBuilder.() -> Unit): LinearGraph {
  val builder = TestGraphBuilder()
  builder.f()
  return builder.done()
}

private fun <T, R> Iterable<T>.map2(transform: (Int, T) -> R): List<R> {
  val result = ArrayList<R>()
  var index = 0
  for (element in this) {
    result.add(transform(index, element))
    index++
  }
  return result
}

class TestPermanentGraphInfo(
    val graph: LinearGraph,
    vararg val headsOrder: Int = IntArray(0),
    val branchNodes: Set<Int> = setOf()
) : PermanentGraphInfo<Int> {

  val commitInfo = object : PermanentCommitsInfo<Int> {
    override fun getCommitId(nodeId: Int) = nodeId
    override fun getTimestamp(nodeId: Int) = nodeId.toLong()
    override fun getNodeId(commitId: Int) = commitId
    override fun convertToNodeIds(heads: MutableCollection<Int>) = ContainerUtil.newHashSet(heads)
  }

  val timestampGetter = object : TimestampGetter {
    override fun size() = graph.nodesCount()
    override fun getTimestamp(index: Int) = commitInfo.getTimestamp(graph.getNodeId(index))
  }

  val graphLayout = GraphLayoutBuilder.build(graph) {x, y ->
    if (headsOrder.isEmpty()) {
      graph.getNodeId(x) - graph.getNodeId(y)
    } else {
      val t = if (headsOrder.indexOf(x) == -1) x else if (headsOrder.indexOf(y) == -1) y else -1
      if (t != -1) throw IllegalStateException("Not found headsOrder for $t node by id")
      headsOrder.indexOf(x) - headsOrder.indexOf(y)
    }
  }

  object colorManager : GraphColorManager<Int> {
    override fun getColorOfBranch(headCommit: Int): Int = headCommit

    override fun getColorOfFragment(headCommit: Int?, magicIndex: Int): Int = magicIndex

    override fun compareHeads(head1: Int, head2: Int): Int = head1.compareTo(head2)
  }

  override fun getPermanentCommitsInfo() = commitInfo
  override fun getPermanentLinearGraph(): PermanentLinearGraphImpl = object : PermanentLinearGraphImpl(), LinearGraph by graph {}
  override fun getPermanentGraphLayout() = graphLayout
  override fun getBranchNodeIds() = branchNodes

  override fun getGraphColorManager() = colorManager
}

class TestLinearController(val graph: LinearGraph) : LinearGraphController {
  override fun getCompiledGraph() = graph

  override fun performLinearGraphAction(action: LinearGraphController.LinearGraphAction) = throw UnsupportedOperationException()
}

public fun LinearGraph.asVisibleGraph(): VisibleGraph<Int> = VisibleGraphImpl(TestLinearController(this), TestPermanentGraphInfo(this))
