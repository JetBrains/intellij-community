// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.impl.facade

import com.intellij.vcs.log.graph.GraphCommit
import com.intellij.vcs.log.graph.GraphCommitImpl
import com.intellij.vcs.log.graph.PermanentGraph
import com.intellij.vcs.log.graph.VisibleGraph
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo
import com.intellij.vcs.log.graph.api.printer.GraphColorGetterFactory
import com.intellij.vcs.log.graph.collapsing.BranchFilterController
import com.intellij.vcs.log.graph.collapsing.CollapsedController
import com.intellij.vcs.log.graph.impl.facade.sort.SortIndexMap
import com.intellij.vcs.log.graph.impl.facade.sort.SortedBaseController
import com.intellij.vcs.log.graph.impl.facade.sort.bek.BekSorter
import com.intellij.vcs.log.graph.impl.permanent.*
import com.intellij.vcs.log.graph.linearBek.LinearBekController
import com.intellij.vcs.log.graph.utils.LinearGraphUtils
import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet
import org.jetbrains.annotations.ApiStatus
import java.util.*
import java.util.function.BiConsumer
import java.util.function.Function
import java.util.function.Predicate

class PermanentGraphImpl<CommitId : Any> private constructor(private val permanentLinearGraph: PermanentLinearGraphImpl,
                                                             private val permanentGraphLayout: GraphLayoutImpl,
                                                             private val permanentCommitsInfo: PermanentCommitsInfoImpl<CommitId>,
                                                             colorGetterFactory: GraphColorGetterFactory<CommitId>,
                                                             branchesCommitId: Set<CommitId>) : PermanentGraph<CommitId>, PermanentGraphInfo<CommitId> {
  private val branchNodeIds: Set<Int> = permanentCommitsInfo.convertToNodeIds(branchesCommitId)

  private val bekIntMap: SortIndexMap by lazy {
    BekSorter.createBekMap(permanentLinearGraph, permanentGraphLayout, permanentCommitsInfo.timestampGetter)
  }

  private val graphColorGetter = colorGetterFactory.createColorGetter(this)
  private val reachableNodes = ReachableNodes(LinearGraphUtils.asLiteLinearGraph(permanentLinearGraph))

  private fun createFilteredController(options: PermanentGraph.Options, visibleHeads: Set<CommitId>?, matchingCommits: Set<CommitId>?): LinearGraphController {
    val visibleHeadsIds = if (visibleHeads != null) permanentCommitsInfo.convertToNodeIds(visibleHeads) else null
    val matchingCommitIds = if (matchingCommits != null) permanentCommitsInfo.convertToNodeIds(matchingCommits) else null

    when (options) {
      is PermanentGraph.Options.Base -> {
        val baseController = when (options.sortType) {
          PermanentGraph.SortType.Normal -> BaseController(this)
          PermanentGraph.SortType.Bek -> SortedBaseController(this, bekIntMap)
        }
        if (matchingCommitIds != null) {
          return FilteredController.create(baseController, this, matchingCommitIds, visibleHeadsIds)
        }
        return CollapsedController(baseController, this, visibleHeadsIds)
      }
      PermanentGraph.Options.FirstParent -> {
        val baseController = BaseController(this)
        return FirstParentController.create(baseController, this, matchingCommitIds, visibleHeadsIds)
      }
      PermanentGraph.Options.LinearBek -> {
        val baseController = LinearBekController(SortedBaseController(this, bekIntMap), this)
        if (matchingCommitIds != null) {
          return FilteredController.create(baseController, this, matchingCommitIds, visibleHeadsIds)
        }
        if (visibleHeadsIds != null) {
          return BranchFilterController(baseController, this, visibleHeadsIds)
        }
        return baseController
      }
    }
  }

  fun createVisibleGraph(options: PermanentGraph.Options,
                         visibleHeads: Set<CommitId>?,
                         matchingCommits: Set<CommitId>?,
                         preprocessor: BiConsumer<in LinearGraphController, in PermanentGraphInfo<CommitId>>): VisibleGraph<CommitId> {
    val controller = createFilteredController(options, visibleHeads, matchingCommits)
    preprocessor.accept(controller, this)
    return VisibleGraphImpl(controller, this, graphColorGetter)
  }

  override fun createVisibleGraph(options: PermanentGraph.Options,
                                  headsOfVisibleBranches: Set<CommitId>?,
                                  matchedCommits: Set<CommitId>?): VisibleGraph<CommitId> {
    return createVisibleGraph(options, headsOfVisibleBranches, matchedCommits) { _: LinearGraphController?, _: PermanentGraphInfo<CommitId>? -> }
  }

  override val allCommits: List<GraphCommit<CommitId>>
    get() = object : AbstractList<GraphCommit<CommitId>>() {
      override fun get(index: Int): GraphCommit<CommitId> {
        val commitId = permanentCommitsInfo.getCommitId(index)
        val downNodes = LinearGraphUtils.getDownNodesIncludeNotLoad(permanentLinearGraph, index)
        val parentsCommitIds = permanentCommitsInfo.convertToCommitIdList(downNodes)
        return GraphCommitImpl.createCommit(commitId, parentsCommitIds, permanentCommitsInfo.getTimestamp(index))
      }

      override val size get() = permanentLinearGraph.nodesCount()
    }

  override fun getChildren(commit: CommitId): List<CommitId> {
    val commitIndex = permanentCommitsInfo.getNodeId(commit)
    return permanentCommitsInfo.convertToCommitIdList(LinearGraphUtils.getUpNodes(permanentLinearGraph, commitIndex))
  }

  override fun getContainingBranches(commit: CommitId): Set<CommitId> {
    val commitIndex = permanentCommitsInfo.getNodeId(commit)
    return permanentCommitsInfo.convertToCommitIdSet(reachableNodes.getContainingBranches(commitIndex, branchNodeIds))
  }

  override fun getContainedInBranchCondition(currentBranchHead: Collection<CommitId>): Predicate<CommitId> {
    val headIds = currentBranchHead.map { head: CommitId -> permanentCommitsInfo.getNodeId(head) }
    if (currentBranchHead.firstOrNull() is Int) {
      val branchNodes: IntSet = IntOpenHashSet()
      reachableNodes.walkDown(headIds) { node: Int -> branchNodes.add((permanentCommitsInfo.getCommitId(node) as Int)) }
      return IntContainedInBranchCondition(branchNodes)
    }
    val branchNodes = HashSet<CommitId>()
    reachableNodes.walkDown(headIds) { node: Int -> branchNodes.add(permanentCommitsInfo.getCommitId(node)) }
    return ContainedInBranchCondition(branchNodes)
  }

  @ApiStatus.Internal
  override fun getPermanentCommitsInfo(): PermanentCommitsInfoImpl<CommitId> = permanentCommitsInfo
  
  override fun getLinearGraph(): PermanentLinearGraphImpl = permanentLinearGraph
  
  @ApiStatus.Internal
  override fun getPermanentGraphLayout(): GraphLayoutImpl = permanentGraphLayout
  
  override fun getBranchNodeIds(): Set<Int> = branchNodeIds

  private class NotLoadedCommitsIdsGenerator<CommitId> : Function<CommitId, Int> {
    val notLoadedCommits: Int2ObjectMap<CommitId> = Int2ObjectOpenHashMap()

    override fun apply(dom: CommitId): Int {
      val nodeId: Int = -(notLoadedCommits.size + 2)
      notLoadedCommits.put(nodeId, dom)
      return nodeId
    }
  }

  private class IntContainedInBranchCondition<CommitId>(private val myBranchNodes: IntSet) : Predicate<CommitId> {
    override fun test(commitId: CommitId): Boolean {
      return myBranchNodes.contains((commitId as Int))
    }
  }

  private class ContainedInBranchCondition<CommitId>(private val myBranchNodes: Set<CommitId>) : Predicate<CommitId> {
    override fun test(commitId: CommitId): Boolean {
      return myBranchNodes.contains(commitId)
    }
  }

  companion object {
    /**
     * Create a new instance of PermanentGraph.
     *
     * @param graphCommits          topologically sorted list of commits in the graph
     * @param colorGetterFactory    color generator factory for the graph
     * @param headCommitsComparator compares two head commits, which represent graph branches, by expected positions of these branches in the graph,
     *                              and thus by their "importance". If branch1 is more important than branch2,
     *                              branch1 will be laid out more to the left from the branch2,
     *                              and the color of branch1 will be reused by the subgraph below the point when these branches have diverged.
     * @param branchesCommitId      commit ids of all the branch heads
     * @param <CommitId>            commit id type
     * @return new instance of PermanentGraph
     * @see com.intellij.vcs.log.VcsLogRefManager.getBranchLayoutComparator
     */
    @JvmStatic
    fun <CommitId : Any> newInstance(graphCommits: List<GraphCommit<CommitId>>,
                                     colorGetterFactory: GraphColorGetterFactory<CommitId>,
                                     headCommitsComparator: Comparator<CommitId>,
                                     branchesCommitId: Set<CommitId>): PermanentGraphImpl<CommitId> {
      val idsGenerator = NotLoadedCommitsIdsGenerator<CommitId>()
      val linearGraph = PermanentLinearGraphBuilder.newInstance(graphCommits).build(idsGenerator)
      val permanentCommitsInfo = PermanentCommitsInfoImpl.newInstance(graphCommits, idsGenerator.notLoadedCommits)
      val branchIndexes = permanentCommitsInfo.convertToNodeIds(branchesCommitId, true)
      val permanentGraphLayout = GraphLayoutBuilder.build(linearGraph, branchIndexes) { nodeIndex1: Int, nodeIndex2: Int ->
        val commitId1 = permanentCommitsInfo.getCommitId(nodeIndex1)
        val commitId2 = permanentCommitsInfo.getCommitId(nodeIndex2)
        headCommitsComparator.compare(commitId1, commitId2)
      }

      return PermanentGraphImpl(linearGraph, permanentGraphLayout, permanentCommitsInfo, colorGetterFactory, branchesCommitId)
    }
  }
}
