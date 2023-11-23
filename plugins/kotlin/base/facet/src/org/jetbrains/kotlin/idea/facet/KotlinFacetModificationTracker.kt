// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.facet

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.backend.workspace.WorkspaceModelTopics
import com.intellij.platform.workspace.jps.entities.FacetEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleSettingsBase
import com.intellij.platform.workspace.storage.VersionedStorageChange
import org.jetbrains.kotlin.idea.base.util.caching.newEntity
import org.jetbrains.kotlin.idea.base.util.caching.oldEntity
import org.jetbrains.kotlin.idea.workspaceModel.KotlinSettingsEntity

class KotlinFacetModificationTracker(project: Project) :
    SimpleModificationTracker(), WorkspaceModelChangeListener, Disposable {

    init {
        project.messageBus.connect(this).subscribe(WorkspaceModelTopics.CHANGED, this)
    }

    override fun changed(event: VersionedStorageChange) {
        val moduleChanges = event.getChanges(ModuleEntity::class.java)
        val facetChanges = event.getChanges(FacetEntity::class.java) + event.getChanges(KotlinSettingsEntity::class.java)
        if (moduleChanges.isEmpty() && facetChanges.isEmpty()) return

        for (facetChange in facetChanges) {
            val kotlinFacetEntity = facetChange.oldEntity()?.takeIf { it.isKotlinFacet() } ?: facetChange.newEntity()
                ?.takeIf { it.isKotlinFacet() }
            if (kotlinFacetEntity != null) {
                incModificationCount()
                return
            }
        }

        for (moduleChange in moduleChanges) {
            val kotlinModuleEntity =
                moduleChange.oldEntity()?.takeIf { it.facets.any { facet -> facet.isKotlinFacet() } } ?: moduleChange.newEntity()
                    ?.takeIf { it.facets.any { facet -> facet.isKotlinFacet() } }
            if (kotlinModuleEntity != null) {
                incModificationCount()
                return
            }
        }
    }

    override fun dispose() = Unit

    companion object {

        @JvmStatic
        fun getInstance(project: Project): KotlinFacetModificationTracker = project.service()
    }
}

fun ModuleSettingsBase.isKotlinFacet(): Boolean = name == KotlinFacetType.NAME
