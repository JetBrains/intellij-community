// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.VcsLogAggregatedStoredRefs
import com.intellij.vcs.log.VcsLogCommitStorageIndex
import com.intellij.vcs.log.VcsLogProvider
import com.intellij.vcs.log.graph.PermanentGraph
import org.jetbrains.annotations.ApiStatus

/**
 * Previously known as `DataPack`.
 * Represents the graph data for all VCS log instances of the [com.intellij.vcs.log.impl.VcsLogManager] regardless of the filters set.
 */
sealed interface VcsLogGraphData {
  val logProviders: Map<VirtualFile, VcsLogProvider>
  val permanentGraph: PermanentGraph<VcsLogCommitStorageIndex>
  val refsModel: VcsLogAggregatedStoredRefs
  val isFull: Boolean

  /**
   * Previously known as `SmallDataPack`.
   * Optimization to quickly load a small chunk of graph and display it over the previously loaded (if any) data
   * while loading the actual graph data.
   *
   * @see [com.intellij.vcs.log.visible.CompoundVisiblePack]
   */
  interface OverlayData : VcsLogGraphData {
    @ApiStatus.Internal
    companion object {
      internal val commitsCount: Int
        get() = Registry.intValue("vcs.log.small.data.pack.commits.count")

      internal val isEnabled: Boolean
        get() = commitsCount > 0 && !ApplicationManager.getApplication().isUnitTestMode()
    }
  }

  class Error(val error: Throwable) : VcsLogGraphData {
    override val logProviders: Map<VirtualFile, VcsLogProvider> = emptyMap()
    override val permanentGraph: PermanentGraph<VcsLogCommitStorageIndex> = EmptyPermanentGraph.getInstance()
    override val refsModel: VcsLogAggregatedStoredRefs = RefsModel.createEmptyInstance(EmptyLogStorage.INSTANCE)
    override val isFull: Boolean = false
  }

  object Empty : VcsLogGraphData {
    override val logProviders: Map<VirtualFile, VcsLogProvider> = emptyMap()
    override val permanentGraph: PermanentGraph<VcsLogCommitStorageIndex> = EmptyPermanentGraph.getInstance()
    override val refsModel: VcsLogAggregatedStoredRefs = RefsModel.createEmptyInstance(EmptyLogStorage.INSTANCE)
    override val isFull: Boolean = false
  }
}
