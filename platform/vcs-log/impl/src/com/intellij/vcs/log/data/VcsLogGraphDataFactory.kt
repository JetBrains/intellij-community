// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data

import com.intellij.openapi.vcs.telemetry.VcsBackendTelemetrySpan
import com.intellij.openapi.vcs.telemetry.VcsBackendTelemetrySpan.LogData.BuildingGraphData
import com.intellij.openapi.vcs.telemetry.VcsBackendTelemetrySpan.LogData.CreatingRefsModel
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.diagnostic.telemetry.TelemetryManager.Companion.getInstance
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.platform.vcs.impl.shared.telemetry.VcsScope
import com.intellij.platform.vcs.impl.shared.telemetry.VcsTracer
import com.intellij.platform.vcs.impl.shared.telemetry.trace
import com.intellij.vcs.log.VcsLogAggregatedStoredRefs
import com.intellij.vcs.log.VcsLogCommitStorageIndex
import com.intellij.vcs.log.VcsLogProvider
import com.intellij.vcs.log.VcsLogRootStoredRefs
import com.intellij.vcs.log.branches
import com.intellij.vcs.log.graph.GraphColorManagerImpl
import com.intellij.vcs.log.graph.GraphCommit
import com.intellij.vcs.log.graph.HeadCommitsComparator
import com.intellij.vcs.log.graph.PermanentGraph
import com.intellij.vcs.log.graph.impl.facade.PermanentGraphImpl
import com.intellij.vcs.log.graph.impl.print.GraphColorGetterByHeadFactory
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

@ApiStatus.Internal
object VcsLogGraphDataFactory {
  @JvmStatic
  fun buildData(
    commits: List<GraphCommit<VcsLogCommitStorageIndex>>,
    refs: Map<VirtualFile, VcsLogRootStoredRefs>,
    providers: Map<VirtualFile, VcsLogProvider>,
    storage: VcsLogStorage,
    full: Boolean,
  ): VcsLogGraphData = VcsTracer.trace(BuildingGraphData) {
    val refsModel = VcsTracer.trace(CreatingRefsModel) { RefsModel.create(refs, storage, providers) }
    val permanentGraph = buildPermanentGraph(commits, refsModel, providers, storage)
    buildData(refsModel, permanentGraph, providers, full)
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
  ): VcsLogGraphData.OverlayData = VcsTracer.trace(BuildingGraphData) {
    val refsModel = VcsTracer.trace(CreatingRefsModel) { RefsModel.create(refs, storage, providers) }
    val permanentGraph = buildPermanentGraph(commits, refsModel, providers, storage)
    VcsLogGraphOverlayData(refsModel, permanentGraph, providers)
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
