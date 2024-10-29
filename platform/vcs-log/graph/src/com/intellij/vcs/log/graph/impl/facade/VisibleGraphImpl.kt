// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.impl.facade

import com.intellij.vcs.log.graph.*
import com.intellij.vcs.log.graph.actions.ActionController
import com.intellij.vcs.log.graph.actions.GraphAction
import com.intellij.vcs.log.graph.actions.GraphAnswer
import com.intellij.vcs.log.graph.api.LinearGraph
import com.intellij.vcs.log.graph.api.elements.GraphEdge
import com.intellij.vcs.log.graph.api.elements.GraphEdgeType
import com.intellij.vcs.log.graph.api.elements.GraphNodeType
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo
import com.intellij.vcs.log.graph.api.printer.GraphColorGetter
import com.intellij.vcs.log.graph.api.printer.GraphPrintElement
import com.intellij.vcs.log.graph.impl.facade.LinearGraphController.LinearGraphAction
import com.intellij.vcs.log.graph.impl.facade.LinearGraphController.LinearGraphAnswer
import com.intellij.vcs.log.graph.impl.print.GraphElementComparatorByLayoutIndex
import com.intellij.vcs.log.graph.impl.print.PrintElementGeneratorImpl
import com.intellij.vcs.log.graph.utils.LinearGraphUtils
import org.jetbrains.annotations.ApiStatus
import java.awt.Cursor

@ApiStatus.Internal
class VisibleGraphImpl<CommitId : Any>(private val graphController: LinearGraphController,
                                       val permanentGraph: PermanentGraphInfo<CommitId>,
                                       private val colorGenerator: GraphColorGetter) : VisibleGraph<CommitId> {
  private lateinit var presentationManager: PrintElementPresentationManagerImpl<CommitId>
  private lateinit var printElementGenerator: PrintElementGeneratorImpl
  private var isShowLongEdges = false

  init {
    updatePrintElementGenerator()
  }

  override fun getVisibleCommitCount() = graphController.compiledGraph.nodesCount()

  override fun getRowInfo(visibleRow: Int): RowInfo<CommitId> {
    val nodeId = graphController.compiledGraph.getNodeId(visibleRow)
    assert(nodeId >= 0) // todo remake for all id
    return RowInfoImpl(nodeId, visibleRow)
  }

  override fun getVisibleRowIndex(commitId: CommitId): Int? {
    val nodeId = permanentGraph.permanentCommitsInfo.getNodeId(commitId)
    return graphController.compiledGraph.getNodeIndex(nodeId)
  }

  override fun getActionController(): ActionController<CommitId> = ActionControllerImpl()

  fun updatePrintElementGenerator() {
    presentationManager = PrintElementPresentationManagerImpl(permanentGraph, linearGraph, colorGenerator)
    val comparator = GraphElementComparatorByLayoutIndex { nodeIndex: Int ->
      val nodeId = linearGraph.getNodeId(nodeIndex)
      if (nodeId < 0) return@GraphElementComparatorByLayoutIndex nodeId
      permanentGraph.permanentGraphLayout.getLayoutIndex(nodeId)
    }
    printElementGenerator = PrintElementGeneratorImpl(linearGraph, presentationManager, isShowLongEdges, comparator)
  }

  fun buildSimpleGraphInfo(visibleRow: Int, visibleRange: Int): SimpleGraphInfo<CommitId> {
    return SimpleGraphInfo.build(graphController.compiledGraph,
                                 permanentGraph.permanentGraphLayout,
                                 permanentGraph.permanentCommitsInfo,
                                 permanentGraph.linearGraph.nodesCount(),
                                 permanentGraph.branchNodeIds, visibleRow, visibleRange)
  }

  override fun getRecommendedWidth(): Int = printElementGenerator.recommendedWidth

  val linearGraph: LinearGraph
    get() = graphController.compiledGraph

  override fun toString(): String {
    val commits = mutableListOf<CommitId>()
    for (i in 0 until visibleCommitCount) {
      commits.add(getRowInfo(i).commit)
    }
    return "VisibleGraph[${commits.joinToString(", ")}]"
  }

  private inner class ActionControllerImpl : ActionController<CommitId> {
    private fun convertToNodeId(nodeIndex: Int?): Int? {
      return if (nodeIndex == null) null else graphController.compiledGraph.getNodeId(nodeIndex)
    }

    private fun performArrowAction(action: LinearGraphAction): GraphAnswer<CommitId>? {
      val affectedElement = action.affectedElement
      if (affectedElement !is EdgePrintElement) return null

      val edgePrintElement = affectedElement as EdgePrintElement
      if (!edgePrintElement.hasArrow()) return null

      val edge = affectedElement.graphElement as? GraphEdge ?: return null

      var targetId: Int? = null
      if (edge.type == GraphEdgeType.NOT_LOAD_COMMIT) {
        assert(edgePrintElement.type == EdgePrintElement.Type.DOWN)
        targetId = edge.targetId
      }
      if (edge.type.isNormalEdge) {
        targetId = if (edgePrintElement.type == EdgePrintElement.Type.DOWN) {
          convertToNodeId(edge.downNodeIndex)
        }
        else {
          convertToNodeId(edge.upNodeIndex)
        }
      }
      if (targetId == null) return null

      if (action.type == GraphAction.Type.MOUSE_OVER) {
        val selectionChanged = presentationManager.setSelectedElement(affectedElement)
        return GraphAnswerImpl(LinearGraphUtils.getCursor(true), permanentGraph.permanentCommitsInfo.getCommitId(targetId), null,
                               false, selectionChanged)
      }
      if (action.type == GraphAction.Type.MOUSE_CLICK) {
        val selectionChanged = presentationManager.setSelectedElements(emptySet())
        return GraphAnswerImpl(LinearGraphUtils.getCursor(false), permanentGraph.permanentCommitsInfo.getCommitId(targetId), null,
                               true, selectionChanged)
      }
      return null
    }

    override fun performAction(graphAction: GraphAction): GraphAnswer<CommitId> {
      val action = convert(graphAction)
      val graphAnswer = performArrowAction(action)
      if (graphAnswer != null) return graphAnswer

      val answer = graphController.performLinearGraphAction(action)
      val selectionChanged = if (answer.selectedNodeIds != null) {
        presentationManager.setSelectedElements(answer.selectedNodeIds!!)
      }
      else {
        presentationManager.setSelectedElements(emptySet())
      }
      if (answer.graphChanges != null) updatePrintElementGenerator()
      return convert(answer, selectionChanged)
    }

    override fun areLongEdgesHidden() = !isShowLongEdges

    override fun setLongEdgesHidden(longEdgesHidden: Boolean) {
      isShowLongEdges = !longEdgesHidden
      updatePrintElementGenerator()
    }

    private fun convert(graphAction: GraphAction): LinearGraphAction {
      val printElement = graphAction.affectedElement?.let { affectedElement ->
        if (affectedElement is GraphPrintElement) {
          affectedElement
        }
        else {
          printElementGenerator.getPrintElements(affectedElement.rowIndex).find { it == affectedElement }
          ?: throw throw IllegalStateException("Not found graphElement for this printElement: $affectedElement")
        }
      }
      return LinearGraphActionImpl(printElement, graphAction.type)
    }

    private fun convert(answer: LinearGraphAnswer, selectionChanged: Boolean): GraphAnswer<CommitId> {
      val updater = answer.graphUpdater?.let {
        Runnable {
          it.run()
          updatePrintElementGenerator()
        }
      }
      return GraphAnswerImpl(answer.cursorToSet, null, updater, false, selectionChanged)
    }

    override fun isActionSupported(action: GraphAction): Boolean {
      if (action.type == GraphAction.Type.BUTTON_COLLAPSE || action.type == GraphAction.Type.BUTTON_EXPAND) {
        return graphController !is FilteredController
      }
      return super.isActionSupported(action)
    }
  }

  private class GraphAnswerImpl<CommitId>(private val cursor: Cursor?,
                                          private val commitToJump: CommitId?,
                                          private val updater: Runnable?,
                                          private val doJump: Boolean,
                                          private val isRepaintRequired: Boolean) : GraphAnswer<CommitId> {
    override fun getCursorToSet() = cursor
    override fun getCommitToJump() = commitToJump
    override fun getGraphUpdater() = updater
    override fun doJump() = doJump
    override fun isRepaintRequired() = isRepaintRequired
  }

  data class LinearGraphActionImpl(override val affectedElement: GraphPrintElement?, override val type: GraphAction.Type) : LinearGraphAction

  private inner class RowInfoImpl(private val nodeId: Int, private val visibleRow: Int) : RowInfo<CommitId> {
    override fun getCommit(): CommitId {
      return permanentGraph.permanentCommitsInfo.getCommitId(nodeId)
    }

    override fun getOneOfHeads(): CommitId {
      val headNodeId = permanentGraph.permanentGraphLayout.getOneOfHeadNodeIndex(nodeId)
      return permanentGraph.permanentCommitsInfo.getCommitId(headNodeId)
    }

    override fun getPrintElements(): Collection<PrintElement> {
      return printElementGenerator.getPrintElements(visibleRow)
    }

    override fun getRowType(): RowType {
      return when (val nodeType = graphController.compiledGraph.getGraphNode(visibleRow).type) {
        GraphNodeType.USUAL -> RowType.NORMAL
        GraphNodeType.UNMATCHED -> RowType.UNMATCHED
        else -> throw UnsupportedOperationException("Unsupported node type: $nodeType")
      }
    }

    override fun getAdjacentRows(parent: Boolean): List<Int> {
      return if (parent) LinearGraphUtils.getDownNodes(graphController.compiledGraph, visibleRow)
      else LinearGraphUtils.getUpNodes(graphController.compiledGraph, visibleRow)
    }
  }
}
