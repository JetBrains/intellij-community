// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.workspaceModel

import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ExternalProjectSystemRegistry
import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.platform.workspace.jps.entities.modifyModuleEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.workspaceModel.ide.impl.legacyBridge.facet.FacetBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.facet.FacetConfigurationBridge
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.idea.facet.KotlinFacetConfiguration

class KotlinFacetBridge(
    module: Module,
    name: String,
    configuration: KotlinFacetConfiguration
) : KotlinFacet(module, name, configuration),
    FacetBridge<KotlinSettingsEntity, KotlinSettingsEntityBuilder> {
    override val config: FacetConfigurationBridge<KotlinSettingsEntity, KotlinSettingsEntityBuilder>
        get() = configuration as KotlinFacetConfigurationBridge

    override fun updateExistingEntityInStorage(existingFacetEntity: KotlinSettingsEntity, mutableStorage: MutableEntityStorage) {
        val moduleEntity = mutableStorage.resolve(existingFacetEntity.moduleId)!!
        mutableStorage.modifyModuleEntity(moduleEntity) module@{
            val kotlinSettingsEntity = config.getEntityBuilder(this@module)
            mutableStorage.modifyKotlinSettingsEntity(existingFacetEntity) {
                if (kotlinSettingsEntity.flushNeeded) flushNeeded = false
                name = kotlinSettingsEntity.name
                sourceRoots = kotlinSettingsEntity.sourceRoots.toMutableList()
                configFileItems = kotlinSettingsEntity.configFileItems.toMutableList()
                moduleId = kotlinSettingsEntity.moduleId
                module = this@module
                useProjectSettings = kotlinSettingsEntity.useProjectSettings
                implementedModuleNames = kotlinSettingsEntity.implementedModuleNames.toMutableList()
                dependsOnModuleNames = kotlinSettingsEntity.dependsOnModuleNames.toMutableList()
                additionalVisibleModuleNames = kotlinSettingsEntity.additionalVisibleModuleNames.toMutableSet()
                productionOutputPath = kotlinSettingsEntity.productionOutputPath
                testOutputPath = kotlinSettingsEntity.testOutputPath
                sourceSetNames = kotlinSettingsEntity.sourceSetNames.toMutableList()
                isTestModule = kotlinSettingsEntity.isTestModule
                externalProjectId = kotlinSettingsEntity.externalProjectId
                isHmppEnabled = kotlinSettingsEntity.isHmppEnabled
                pureKotlinSourceFolders = kotlinSettingsEntity.pureKotlinSourceFolders.toMutableList()
                kind = kotlinSettingsEntity.kind
                compilerArguments = kotlinSettingsEntity.compilerArguments
                compilerSettings = kotlinSettingsEntity.compilerSettings
                targetPlatform = kotlinSettingsEntity.targetPlatform
                externalSystemRunTasks = kotlinSettingsEntity.externalSystemRunTasks.toMutableList()
                version = kotlinSettingsEntity.version
            }
        }
    }

    override fun getExternalSource(): ProjectModelExternalSource? {
        return super.getExternalSource() ?: run {
            val modulePropertyManager = ExternalSystemModulePropertyManager.getInstance(module)
            modulePropertyManager.getExternalSystemId()?.let { externalSystemId ->
                ExternalProjectSystemRegistry.getInstance().getSourceById(externalSystemId)
            }
        }
    }
}