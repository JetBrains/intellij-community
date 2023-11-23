// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.workspaceModel

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ExternalProjectSystemRegistry
import com.intellij.openapi.roots.ProjectModelExternalSource
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
    FacetBridge<KotlinSettingsEntity> {
    override val config: FacetConfigurationBridge<KotlinSettingsEntity>
        get() = configuration as KotlinFacetConfigurationBridge

    override fun updateExistingEntityInStorage(existingFacetEntity: KotlinSettingsEntity, mutableStorage: MutableEntityStorage) {
        val moduleEntity = mutableStorage.resolve(existingFacetEntity.moduleId)!!
        val kotlinSettingsEntity = config.getEntity(moduleEntity)
        mutableStorage.modifyEntity(existingFacetEntity) {
            name = kotlinSettingsEntity.name
            sourceRoots = kotlinSettingsEntity.sourceRoots.toMutableList()
            configFileItems = kotlinSettingsEntity.configFileItems.toMutableList()
            moduleId = kotlinSettingsEntity.moduleId
            module = mutableStorage.resolve(kotlinSettingsEntity.moduleId)!!
            useProjectSettings = kotlinSettingsEntity.useProjectSettings
            compilerArguments = kotlinSettingsEntity.compilerArguments
            compilerSettings = kotlinSettingsEntity.compilerSettings
            implementedModuleNames = kotlinSettingsEntity.implementedModuleNames.toMutableList()
            dependsOnModuleNames = kotlinSettingsEntity.dependsOnModuleNames.toMutableList()
            additionalVisibleModuleNames = kotlinSettingsEntity.additionalVisibleModuleNames.toMutableSet()
            productionOutputPath = kotlinSettingsEntity.productionOutputPath
            testOutputPath = kotlinSettingsEntity.testOutputPath
            kind = kotlinSettingsEntity.kind
            sourceSetNames = kotlinSettingsEntity.sourceSetNames.toMutableList()
            isTestModule = kotlinSettingsEntity.isTestModule
            externalProjectId = kotlinSettingsEntity.externalProjectId
            isHmppEnabled = kotlinSettingsEntity.isHmppEnabled
            pureKotlinSourceFolders = kotlinSettingsEntity.pureKotlinSourceFolders.toMutableList()
            targetPlatform = kotlinSettingsEntity.targetPlatform
        }
    }

    override fun getExternalSource(): ProjectModelExternalSource? {
        return super.getExternalSource() ?: if (configuration.settings.externalProjectId.isEmpty()) return null
        else ExternalProjectSystemRegistry.getInstance().getSourceById(configuration.settings.externalProjectId)
    }
}