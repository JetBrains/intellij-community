// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data

import com.intellij.openapi.vcs.telemetry.VcsBackendTelemetrySpan
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.diagnostic.telemetry.TelemetryManager.Companion.getInstance
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.platform.vcs.impl.shared.telemetry.VcsScope
import com.intellij.vcs.log.VcsLogCommitStorageIndex
import com.intellij.vcs.log.VcsLogProvider
import com.intellij.vcs.log.graph.GraphColorManagerImpl
import com.intellij.vcs.log.graph.GraphCommit
import com.intellij.vcs.log.graph.HeadCommitsComparator
import com.intellij.vcs.log.graph.PermanentGraph
import com.intellij.vcs.log.graph.impl.facade.PermanentGraphImpl
import com.intellij.vcs.log.graph.impl.print.GraphColorGetterByHeadFactory
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import org.jetbrains.annotations.NonNls

open class DataPack internal constructor(
  refsModel: RefsModel, val permanentGraph: PermanentGraph<VcsLogCommitStorageIndex>,
  providers: Map<VirtualFile, VcsLogProvider>,
  full: Boolean,
) : DataPackBase(providers, refsModel, full) {
  override fun toString(): @NonNls String {
    return "{DataPack. " + permanentGraph.allCommits.size + " commits in " + myLogProviders.keys.size + " roots}"
  }

  class ErrorDataPack(val error: Throwable) : DataPack(RefsModel.createEmptyInstance(EmptyLogStorage.INSTANCE),
                                                       EmptyPermanentGraph.getInstance(), emptyMap(), false)

  companion object {
    @JvmField
    val EMPTY: DataPack = DataPack(RefsModel.createEmptyInstance(EmptyLogStorage.INSTANCE),
                                   EmptyPermanentGraph.getInstance(), emptyMap(), false)

    @JvmStatic
    fun build(
      commits: List<GraphCommit<VcsLogCommitStorageIndex>>, refs: Map<VirtualFile, CompressedRefs>, providers: Map<VirtualFile, VcsLogProvider>,
      storage: VcsLogStorage, full: Boolean,
    ): DataPack {
      val refsModel = RefsModel.create(refs, getHeads(commits), storage, providers)
      val permanentGraph = buildPermanentGraph(commits, refsModel, providers, storage)

      return DataPack(refsModel, permanentGraph, providers, full)
    }
  }
}

class SmallDataPack private constructor(
  refsModel: RefsModel, permanentGraph: PermanentGraph<VcsLogCommitStorageIndex>,
  providers: Map<VirtualFile, VcsLogProvider>,
) :
  DataPack(refsModel, permanentGraph, providers, false) {

  companion object {
    @JvmStatic
    fun build(
      commits: List<GraphCommit<VcsLogCommitStorageIndex>>,
      refs: Map<VirtualFile, CompressedRefs>,
      providers: Map<VirtualFile, VcsLogProvider>,
      storage: VcsLogStorage,
    ): DataPack {
      val refsModel = RefsModel.create(refs, getHeads(commits), storage, providers)
      val permanentGraph = buildPermanentGraph(commits, refsModel, providers, storage)

      return SmallDataPack(refsModel, permanentGraph, providers)
    }
  }
}

private fun buildPermanentGraph(
  commits: List<GraphCommit<VcsLogCommitStorageIndex>>, refsModel: RefsModel, providers: Map<VirtualFile, VcsLogProvider>,
  storage: VcsLogStorage,
): PermanentGraph<VcsLogCommitStorageIndex> {
  if (commits.isEmpty()) return EmptyPermanentGraph.getInstance()

  val headCommitsComparator = HeadCommitsComparator(refsModel, providers.mapValues { it.value.referenceManager }) { commitIndex ->
    storage.getCommitId(commitIndex)?.hash
  }
  val branches = refsModel.branches.mapTo(HashSet()) { storage.getCommitIndex(it.commitHash, it.root) }

  val tracer = getInstance().getTracer(VcsScope)
  return tracer.spanBuilder(VcsBackendTelemetrySpan.LogData.BuildingGraph.getName()).use { span ->
    PermanentGraphImpl.newInstance(commits, GraphColorGetterByHeadFactory(GraphColorManagerImpl(refsModel)), headCommitsComparator, branches)
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