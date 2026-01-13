// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.vcs.telemetry.VcsBackendTelemetrySpan
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.diagnostic.telemetry.TelemetryManager.Companion.getInstance
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.platform.vcs.impl.shared.telemetry.VcsScope
import com.intellij.vcs.log.*
import com.intellij.vcs.log.graph.*
import com.intellij.vcs.log.graph.impl.facade.PermanentGraphImpl
import com.intellij.vcs.log.graph.impl.print.GraphColorGetterByHeadFactory
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import kotlin.time.measureTimedValue

@ApiStatus.Internal
object VcsLogGraphDataFactory {
  private val LOG = logger<VcsLogGraphDataFactory>()

  @JvmStatic
  fun buildData(
    commits: List<GraphCommit<VcsLogCommitStorageIndex>>,
    refs: Map<VirtualFile, VcsLogRootStoredRefs>,
    providers: Map<VirtualFile, VcsLogProvider>,
    storage: VcsLogStorage,
    full: Boolean,
  ): VcsLogGraphData {
    val (heads, headsCalculationTime) = measureTimedValue { getHeads(commits) }
    LOG.trace { "Heads calculated in ${headsCalculationTime.inWholeMilliseconds} ms" }
    val (refsModel, refsModelCreationTime) = measureTimedValue { RefsModel.create(refs, heads, storage, providers) }
    LOG.trace { "Refs model created in ${refsModelCreationTime.inWholeMilliseconds} ms" }
    val (permanentGraph, permanentGraphBuildTime) = measureTimedValue { buildPermanentGraph(commits, refsModel, providers, storage) }
    LOG.trace { "Permanent graph created in ${permanentGraphBuildTime.inWholeMilliseconds} ms" }

    return buildData(refsModel, permanentGraph, providers, full)
  }

  @JvmStatic
  fun buildData(
    refsModel: VcsLogAggregatedStoredRefs,
    permanentGraph: PermanentGraph<VcsLogCommitStorageIndex>,
    logProviders: Map<VirtualFile, VcsLogProvider>,
    full: Boolean,
  ): VcsLogGraphData = VcsLogGraphDataImpl(refsModel, permanentGraph, logProviders, full)

  @JvmStatic
  fun buildOverlayData(
    commits: List<GraphCommit<VcsLogCommitStorageIndex>>,
    refs: Map<VirtualFile, VcsLogRootStoredRefs>,
    providers: Map<VirtualFile, VcsLogProvider>,
    storage: VcsLogStorage,
  ): VcsLogGraphData.OverlayData {
    val refsModel = RefsModel.create(refs, getHeads(commits), storage, providers)
    val permanentGraph = buildPermanentGraph(commits, refsModel, providers, storage)

    return VcsLogGraphOverlayData(refsModel, permanentGraph, providers)
  }

  private fun buildPermanentGraph(
    commits: List<GraphCommit<VcsLogCommitStorageIndex>>,
    refsModel: RefsModel, providers: Map<VirtualFile, VcsLogProvider>,
    storage: VcsLogStorage,
  ): PermanentGraph<VcsLogCommitStorageIndex> {
    if (commits.isEmpty()) return EmptyPermanentGraph.getInstance()

    val headCommitsComparator = HeadCommitsComparator(refsModel, providers.mapValues { it.value.referenceManager }) { commitIndex ->
      storage.getCommitId(commitIndex)?.hash
    }
    val branches = refsModel.branches.mapTo(HashSet()) { storage.getCommitIndex(it.commitHash, it.root) }

    val tracer = getInstance().getTracer(VcsScope)
    return tracer.spanBuilder(VcsBackendTelemetrySpan.LogData.BuildingGraph.getName()).use { span ->
      PermanentGraphImpl.newInstance(commits,
                                     GraphColorGetterByHeadFactory(GraphColorManagerImpl(refsModel)),
                                     headCommitsComparator,
                                     branches)
    }
  }

  private fun getHeads(commits: List<GraphCommit<VcsLogCommitStorageIndex>>): Set<VcsLogCommitStorageIndex> {
    val parents = commits.flatMapTo(IntOpenHashSet()) { it.parents }
    return buildSet {
      for (commit in commits) {
        if (!parents.contains(commit.id)) {
          add(commit.id)
        }
      }
    }
  }
}

private open class VcsLogGraphDataImpl(
  override val refsModel: VcsLogAggregatedStoredRefs,
  override val permanentGraph: PermanentGraph<VcsLogCommitStorageIndex>,
  override val logProviders: Map<VirtualFile, VcsLogProvider>,
  override val isFull: Boolean,
) : VcsLogGraphData {
  override fun toString(): @NonNls String {
    return "{DataPack. " + permanentGraph.allCommits.size + " commits in " + logProviders.keys.size + " roots}"
  }
}

private class VcsLogGraphOverlayData(
  refsModel: VcsLogAggregatedStoredRefs,
  permanentGraph: PermanentGraph<VcsLogCommitStorageIndex>,
  logProviders: Map<VirtualFile, VcsLogProvider>,
) : VcsLogGraphDataImpl(refsModel, permanentGraph, logProviders, isFull = false), VcsLogGraphData.OverlayData {
  override fun toString(): @NonNls String {
    return "{OverlayData. " + permanentGraph.allCommits.size + " commits in " + logProviders.keys.size + " roots}"
  }
}
