// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.facet

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.FacetEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleSettingsFacetBridgeEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.idea.base.util.caching.getChanges
import org.jetbrains.kotlin.idea.base.util.caching.newEntity
import org.jetbrains.kotlin.idea.base.util.caching.oldEntity
import org.jetbrains.kotlin.idea.workspaceModel.KotlinSettingsEntity

@Service(Service.Level.PROJECT)
class KotlinFacetModificationTracker(project: Project, cs: CoroutineScope) : SimpleModificationTracker() {

    init {
        cs.launch {
            WorkspaceModel.getInstance(project).eventLog.collect { event ->
                val moduleChanges = event.getChanges<ModuleEntity>()
                val facetChanges = event.getChanges<FacetEntity>() + event.getChanges<KotlinSettingsEntity>()
                if (moduleChanges.isEmpty() && facetChanges.isEmpty()) return@collect

                for (facetChange in facetChanges) {
                    val kotlinFacetEntity = facetChange.oldEntity()?.takeIf { it.isKotlinFacet() } ?: facetChange.newEntity()
                        ?.takeIf { it.isKotlinFacet() }
                    if (kotlinFacetEntity != null) {
                        incModificationCount()
                        return@collect
                    }
                }

                for (moduleChange in moduleChanges) {
                    val kotlinModuleEntity =
                        moduleChange.oldEntity()?.takeIf { it.facets.any { facet -> facet.isKotlinFacet() } } ?: moduleChange.newEntity()
                            ?.takeIf { it.facets.any { facet -> facet.isKotlinFacet() } }
                    if (kotlinModuleEntity != null) {
                        incModificationCount()
                        return@collect
                    }
                }
            }
        }
    }

    companion object {

        @JvmStatic
        fun getInstance(project: Project): KotlinFacetModificationTracker = project.service()
    }
}

fun ModuleSettingsFacetBridgeEntity.isKotlinFacet(): Boolean = name == KotlinFacetType.NAME
