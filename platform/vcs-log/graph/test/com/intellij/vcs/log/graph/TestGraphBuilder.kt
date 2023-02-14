// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph

import com.intellij.util.containers.ContainerUtil
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
import com.intellij.vcs.log.graph.impl.permanent.GraphLayoutBuilder
import com.intellij.vcs.log.graph.utils.LinearGraphUtils
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

interface BaseTestGraphBuilder {
  val Int.U: SimpleNode get() = SimpleNode(this, GraphNodeType.USUAL)
  val Int.UNM: SimpleNode get() = SimpleNode(this, GraphNodeType.UNMATCHED)
  val Int.NOT_LOAD: SimpleNode get() = SimpleNode(this, GraphNodeType.NOT_LOAD_COMMIT)

  val Int.u: SimpleEdge get() = SimpleEdge(this, GraphEdgeType.USUAL)
  val Int.dot: SimpleEdge get() = SimpleEdge(this, GraphEdgeType.DOTTED)
  val Int?.up_dot: SimpleEdge get() = SimpleEdge(this, GraphEdgeType.DOTTED_ARROW_UP)
  val Int?.down_dot: SimpleEdge get() = SimpleEdge(this, GraphEdgeType.DOTTED_ARROW_DOWN)
  val Int?.not_load: SimpleEdge get() = SimpleEdge(this, GraphEdgeType.NOT_LOAD_COMMIT)

  class SimpleEdge(val toNode: Int?, val type: GraphEdgeType = GraphEdgeType.USUAL)
  class SimpleNode(val nodeId: Int, val type: GraphNodeType = GraphNodeType.USUAL)
}

class TestGraphBuilder : BaseTestGraphBuilder {
  private val nodes = ArrayList<NodeWithEdges>()

  fun done(): LinearGraph = TestLinearGraph(nodes)

  operator fun Int.invoke() = newNode(asSimpleNode())
  operator fun Int.invoke(vararg edge: Int) = newNode(asSimpleNode(), edge.asSimpleEdges())
  operator fun Int.invoke(vararg edge: SimpleEdge) = newNode(asSimpleNode(), edge.toList())
  operator fun SimpleNode.invoke() = newNode(this)
  operator fun SimpleNode.invoke(vararg edge: Int) = newNode(this, edge.asSimpleEdges())
  operator fun SimpleNode.invoke(vararg edge: SimpleEdge) = newNode(this, edge.toList())

  private class NodeWithEdges(val nodeId: Int, val edges: List<SimpleEdge>, val type: GraphNodeType = GraphNodeType.USUAL)

  private fun IntArray.asSimpleEdges() = map { SimpleEdge(it) }
  private fun Int.asSimpleNode() = SimpleNode(this)

  private fun newNode(node: SimpleNode, edges: List<SimpleEdge> = listOf()) {
    nodes.add(NodeWithEdges(node.nodeId, edges, node.type))
  }

  private class TestLinearGraph(buildNodes: List<NodeWithEdges>) : LinearGraph {
    private val nodes: List<GraphNode>
    private val nodeIndexToId: Map<Int, Int>
    private val nodeIdToIndex: Map<Int, Int>
    private val edges = MultiMap<Int, GraphEdge>()

    val SimpleEdge.toIndex: Int? get() = toNode?.let { nodeIdToIndex[it] }

    init {
      val idsMap = HashMap<Int, Int>()
      nodes = buildNodes.map2 { index, it ->
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

          if (edgeType.isNormalEdge) {
            val anotherNodeIndex = simpleEdge.toIndex ?: error(
              "Graph is incorrect. Node ${node.nodeId} has ${edgeType} edge to not existed node: ${simpleEdge.toNode}")

            val graphEdge = GraphEdge.createNormalEdge(anotherNodeIndex, nodeIndex, edgeType)
            edges.putValue(nodeIndex, graphEdge)
            edges.putValue(anotherNodeIndex, graphEdge)
          }
          else {
            edges.putValue(nodeIndex, GraphEdge.createEdgeWithTargetId(nodeIndex, simpleEdge.toNode, edgeType))
          }
        }
      }

    }

    override fun nodesCount() = nodes.size

    override fun getNodeId(nodeIndex: Int): Int = nodeIndexToId[nodeIndex]!!

    override fun getAdjacentEdges(nodeIndex: Int, filter: EdgeFilter): List<GraphEdge> {
      return edges[nodeIndex].filter {
        if (it.type.isNormalEdge) {
          (LinearGraphUtils.isEdgeUp(it, nodeIndex) && filter.upNormal)
          || (LinearGraphUtils.isEdgeDown(it, nodeIndex) && filter.downNormal)
        }
        else {
          filter.special
        }
      }
    }

    override fun getGraphNode(nodeIndex: Int) = nodes[nodeIndex]

    override fun getNodeIndex(nodeId: Int) = nodeIdToIndex[nodeId]

  }
}

private fun LinearGraph.assertEdge(nodeIndex: Int, edge: GraphEdge) {
  if (edge.type.isNormalEdge) {
    if (nodeIndex == edge.upNodeIndex) {
      assertTrue(getAdjacentEdges(edge.downNodeIndex!!, EdgeFilter.NORMAL_UP).contains(edge))
    }
    else {
      assertEquals(nodeIndex, edge.downNodeIndex)
      assertTrue(getAdjacentEdges(edge.upNodeIndex!!, EdgeFilter.NORMAL_DOWN).contains(edge))
    }
  }
  else {
    when (edge.type) {
      GraphEdgeType.NOT_LOAD_COMMIT, GraphEdgeType.DOTTED_ARROW_DOWN -> {
        assertEquals(nodeIndex, edge.upNodeIndex)
        assertNull(edge.downNodeIndex)
      }
      GraphEdgeType.DOTTED_ARROW_UP -> {
        assertEquals(nodeIndex, edge.downNodeIndex)
        assertNull(edge.upNodeIndex)
      }
      else -> {}
    }
  }
}

fun LinearGraph.asTestGraphString(sorted: Boolean = false): String = StringBuilder().apply {
  for (nodeIndex in 0 until nodesCount()) {
    val node = getGraphNode(nodeIndex)
    append(getNodeId(nodeIndex))
    assertEquals(nodeIndex, node.nodeIndex,
                 "nodeIndex: $nodeIndex, but for node with this index(nodeId: ${getNodeId(nodeIndex)}) nodeIndex: ${node.nodeIndex}"
    )
    when (node.type) {
      GraphNodeType.UNMATCHED -> append(".UNM")
      GraphNodeType.NOT_LOAD_COMMIT -> append(".NOT_LOAD")
      else -> {}
    }

    // edges
    var adjacentEdges = getAdjacentEdges(nodeIndex, EdgeFilter.ALL)
    if (sorted) {
      adjacentEdges = adjacentEdges.sortedWith(GraphStrUtils.GRAPH_ELEMENT_COMPARATOR)
    }

    append("(")
    adjacentEdges.mapNotNull {
      assertEdge(nodeIndex, it)
      if (it.upNodeIndex == nodeIndex) {
        val startId = when {
          it.type.isNormalEdge -> getNodeId(it.downNodeIndex!!).toString()
          it.targetId != null -> it.targetId.toString()
          else -> "null"
        }

        when (it.type) {
          GraphEdgeType.USUAL -> startId
          GraphEdgeType.DOTTED -> "$startId.dot"
          GraphEdgeType.DOTTED_ARROW_UP -> "$startId.up_dot"
          GraphEdgeType.DOTTED_ARROW_DOWN -> "$startId.down_dot"
          GraphEdgeType.NOT_LOAD_COMMIT -> "$startId.not_load"
          else -> error("must not happen")
        }
      }
      else {
        null
      }
    }.joinTo(this, separator = ", ") { it }

    append(")")
    append("\n")
  }
}.toString()

fun graph(f: TestGraphBuilder.() -> Unit): LinearGraph {
  val builder = TestGraphBuilder()
  builder.f()
  return builder.done()
}

private fun <T, R> Iterable<T>.map2(transform: (Int, T) -> R): List<R> {
  return mapIndexedTo(ArrayList()) { index, element -> transform(index, element) }
}

class TestPermanentGraphInfo(
  val graph: LinearGraph,
  private vararg val headsOrder: Int = IntArray(0),
  private val branchNodes: Set<Int> = setOf()
) : PermanentGraphInfo<Int> {

  private val commitInfo = object : PermanentCommitsInfo<Int> {
    override fun getCommitId(nodeId: Int) = nodeId
    override fun getTimestamp(nodeId: Int) = nodeId.toLong()
    override fun getNodeId(commitId: Int) = commitId
    override fun convertToNodeIds(heads: Collection<Int>) = heads.toSet()
  }

  private val graphLayout = GraphLayoutBuilder.build(graph) { x, y ->
    if (headsOrder.isEmpty()) {
      graph.getNodeId(x) - graph.getNodeId(y)
    }
    else {
      val t = if (headsOrder.indexOf(x) == -1) x else if (headsOrder.indexOf(y) == -1) y else -1
      if (t != -1) throw IllegalStateException("Not found headsOrder for $t node by id")
      headsOrder.indexOf(x) - headsOrder.indexOf(y)
    }
  }

  override fun getPermanentCommitsInfo() = commitInfo
  override fun getLinearGraph() = graph
  override fun getPermanentGraphLayout() = graphLayout
  override fun getBranchNodeIds() = branchNodes
}