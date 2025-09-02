// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure.modules

import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.storage.WorkspaceEntity
import org.jetbrains.kotlin.idea.base.fir.projectStructure.provider.K2IDEProjectStructureProviderCache

internal fun <M> couldNotResolveEntityError(
    owner: M,
): Nothing where M: KaEntityBasedModule<*, *> {

    error(buildString {
        appendLine("Could not resolve `${owner.entityId}` ")
        appendLine("for `${owner::class}` ")

        if (owner is KaModuleWithDebugData) {
            val project = owner.project
            val cache = K2IDEProjectStructureProviderCache.getInstance(project)
            val data = owner.creationData
            val entityClass = owner.entityInterface

            appendLine("[createdWithoutCaching=${data.createdWithoutCaching}] ")
            appendLine("[createdSource=${data.createdWithoutCaching}, currentSource=${cache.getCacheSourcesTracker().modificationCount}] ")
            appendLine("[createdLibs=${data.createdLibrariesTrackerValue}, currentLibs=${cache.getCacheSdkAndLibrariesTracker().modificationCount}] ")
            runCatching {
                val allEntries = project.workspaceModel.currentSnapshot.entities(entityClass).toList()
                appendLine("[allEntries=${allEntries}] ")
            }.onFailure {
                appendLine("error while collecting all entries: ${it.message}")
                appendLine(it.message)
                appendLine(it.stackTraceToString())
            }
        }
    })
}

internal interface KaModuleWithDebugData {
    val creationData: KaEntityBasedModuleCreationData
    val entityInterface: Class<out WorkspaceEntity>
}

internal data class KaEntityBasedModuleCreationData(
    val createdWithoutCaching: Boolean,
    val createdSourceTrackerValue: Long,
    val createdLibrariesTrackerValue: Long,
)