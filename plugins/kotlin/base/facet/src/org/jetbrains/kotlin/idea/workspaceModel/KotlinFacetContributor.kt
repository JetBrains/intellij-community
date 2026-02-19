// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.workspaceModel

import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.module.Module
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.workspaceModel.ide.legacyBridge.WorkspaceFacetContributor
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.idea.facet.KotlinFacetType

class KotlinFacetContributor: WorkspaceFacetContributor<KotlinSettingsEntity> {
    init {
        if (!KotlinFacetBridgeFactory.kotlinFacetBridgeEnabled) {
            throw ExtensionNotApplicableException.create()
        }
    }

    override val rootEntityType: Class<KotlinSettingsEntity>
        get() = KotlinSettingsEntity::class.java

    override fun getRootEntitiesByModuleEntity(moduleEntity: ModuleEntity): List<KotlinSettingsEntity> = moduleEntity.kotlinSettings

    override fun createFacetFromEntity(entity: KotlinSettingsEntity, module: Module): KotlinFacet {
        val facetType = KotlinFacetType.INSTANCE
        val facetConfigurationBridge = KotlinFacetConfigurationBridge(entity)
        return facetType.createFacet(module, entity.name, facetConfigurationBridge, null)
    }

    override fun getParentModuleEntity(entity: KotlinSettingsEntity): ModuleEntity = entity.module
}