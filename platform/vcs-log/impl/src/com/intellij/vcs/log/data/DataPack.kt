// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.VcsLogAggregatedStoredRefs
import com.intellij.vcs.log.VcsLogCommitStorageIndex
import com.intellij.vcs.log.VcsLogProvider
import com.intellij.vcs.log.graph.PermanentGraph
import org.jetbrains.annotations.ApiStatus

@Deprecated("Use VcsLogGraphData instead")
@ApiStatus.ScheduledForRemoval
open class DataPack internal constructor(
    val refsModel: VcsLogAggregatedStoredRefs,
    val logProviders: Map<VirtualFile, VcsLogProvider>,
    val permanentGraph: PermanentGraph<VcsLogCommitStorageIndex>,
    val isFull: Boolean,
)
