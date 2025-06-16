// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.workspaceModel

import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.workspaceModel.ide.impl.legacyBridge.facet.FacetConfigurationBridge
import org.jetbrains.kotlin.config.IKotlinFacetSettings
import org.jetbrains.kotlin.config.KotlinFacetSettings
import org.jetbrains.kotlin.config.KotlinModuleKind
import org.jetbrains.kotlin.idea.facet.KotlinFacetConfiguration
import org.jetbrains.kotlin.idea.facet.KotlinFacetType
import org.jetbrains.kotlin.idea.serialization.KotlinFacetSettingsWorkspaceModel

class KotlinFacetConfigurationBridge : KotlinFacetConfiguration, FacetConfigurationBridge<KotlinSettingsEntity, KotlinSettingsEntity.Builder> {
    override val settings: IKotlinFacetSettings by lazy { KotlinFacetSettingsWorkspaceModel(kotlinSettingsEntity) }

    private val kotlinSettingsEntity: KotlinSettingsEntity.Builder

    private constructor(kotlinSettingsEntity: KotlinSettingsEntity.Builder) : super() {
        this.kotlinSettingsEntity = kotlinSettingsEntity
    }

    constructor() :
            this(
                KotlinSettingsEntity(name = KotlinFacetType.INSTANCE.presentableName,
                                     moduleId = ModuleId(""),
                                     sourceRoots = emptyList(),
                                     configFileItems = emptyList(),
                                     useProjectSettings = true,
                                     implementedModuleNames = emptyList(),
                                     dependsOnModuleNames = emptyList(),
                                     additionalVisibleModuleNames = emptySet(),
                                     sourceSetNames = emptyList(),
                                     isTestModule = false,
                                     externalProjectId = "",
                                     isHmppEnabled = false,
                                     pureKotlinSourceFolders = emptyList(),
                                     kind = KotlinModuleKind.DEFAULT,
                                     externalSystemRunTasks = emptyList(),
                                     version = KotlinFacetSettings.CURRENT_VERSION,
                                     flushNeeded = false,
                                     entitySource = object : EntitySource {})
            )

    constructor(originKotlinSettingsEntity: KotlinSettingsEntity) :
            this(KotlinSettingsEntity(
                originKotlinSettingsEntity.moduleId,
                originKotlinSettingsEntity.name,
                originKotlinSettingsEntity.sourceRoots,
                originKotlinSettingsEntity.configFileItems,
                originKotlinSettingsEntity.useProjectSettings,
                originKotlinSettingsEntity.implementedModuleNames,
                originKotlinSettingsEntity.dependsOnModuleNames,
                originKotlinSettingsEntity.additionalVisibleModuleNames,
                originKotlinSettingsEntity.sourceSetNames,
                originKotlinSettingsEntity.isTestModule,
                originKotlinSettingsEntity.externalProjectId,
                originKotlinSettingsEntity.isHmppEnabled,
                originKotlinSettingsEntity.pureKotlinSourceFolders,
                originKotlinSettingsEntity.kind,
                originKotlinSettingsEntity.externalSystemRunTasks,
                originKotlinSettingsEntity.version,
                originKotlinSettingsEntity.flushNeeded,
                originKotlinSettingsEntity.entitySource
            ) {
                productionOutputPath = originKotlinSettingsEntity.productionOutputPath
                testOutputPath = originKotlinSettingsEntity.testOutputPath
                compilerArguments = originKotlinSettingsEntity.compilerArguments
                compilerSettings = originKotlinSettingsEntity.compilerSettings
                targetPlatform = originKotlinSettingsEntity.targetPlatform
            })

    override fun init(moduleEntity: ModuleEntity, entitySource: EntitySource) {
        kotlinSettingsEntity.moduleId = moduleEntity.symbolicId
        kotlinSettingsEntity.entitySource = entitySource
    }

    override fun rename(newName: String) {
        kotlinSettingsEntity.name = newName
    }

    override fun getEntityBuilder(moduleEntity: ModuleEntity.Builder): KotlinSettingsEntity.Builder {
        return KotlinSettingsEntity(
            kotlinSettingsEntity.moduleId,
            kotlinSettingsEntity.name,
            kotlinSettingsEntity.sourceRoots,
            kotlinSettingsEntity.configFileItems,
            kotlinSettingsEntity.useProjectSettings,
            kotlinSettingsEntity.implementedModuleNames,
            kotlinSettingsEntity.dependsOnModuleNames,
            kotlinSettingsEntity.additionalVisibleModuleNames,
            kotlinSettingsEntity.sourceSetNames,
            kotlinSettingsEntity.isTestModule,
            kotlinSettingsEntity.externalProjectId,
            kotlinSettingsEntity.isHmppEnabled,
            kotlinSettingsEntity.pureKotlinSourceFolders,
            kotlinSettingsEntity.kind,
            kotlinSettingsEntity.externalSystemRunTasks,
            kotlinSettingsEntity.version,
            kotlinSettingsEntity.flushNeeded,
            kotlinSettingsEntity.entitySource,
        ) {
            module = moduleEntity
            productionOutputPath = kotlinSettingsEntity.productionOutputPath
            testOutputPath = kotlinSettingsEntity.testOutputPath
            compilerArguments = kotlinSettingsEntity.compilerArguments
            compilerSettings = kotlinSettingsEntity.compilerSettings
            targetPlatform = kotlinSettingsEntity.targetPlatform
        }
    }

    override fun update(diffEntity: KotlinSettingsEntity) {
        kotlinSettingsEntity.entitySource = diffEntity.entitySource
        kotlinSettingsEntity.moduleId = diffEntity.moduleId
        kotlinSettingsEntity.sourceRoots = diffEntity.sourceRoots.toMutableList()
        kotlinSettingsEntity.configFileItems = diffEntity.configFileItems.toMutableList()
        kotlinSettingsEntity.useProjectSettings = diffEntity.useProjectSettings
        kotlinSettingsEntity.implementedModuleNames = diffEntity.implementedModuleNames.toMutableList()
        kotlinSettingsEntity.dependsOnModuleNames = diffEntity.dependsOnModuleNames.toMutableList()
        kotlinSettingsEntity.additionalVisibleModuleNames = diffEntity.additionalVisibleModuleNames.toMutableSet()
        kotlinSettingsEntity.productionOutputPath = diffEntity.productionOutputPath
        kotlinSettingsEntity.testOutputPath = diffEntity.testOutputPath
        kotlinSettingsEntity.sourceSetNames = diffEntity.sourceSetNames.toMutableList()
        kotlinSettingsEntity.isTestModule = diffEntity.isTestModule
        kotlinSettingsEntity.externalProjectId = diffEntity.externalProjectId
        kotlinSettingsEntity.isHmppEnabled = diffEntity.isHmppEnabled
        kotlinSettingsEntity.pureKotlinSourceFolders = diffEntity.pureKotlinSourceFolders.toMutableList()
        kotlinSettingsEntity.kind = diffEntity.kind
        kotlinSettingsEntity.compilerArguments = diffEntity.compilerArguments
        kotlinSettingsEntity.compilerSettings = diffEntity.compilerSettings
        kotlinSettingsEntity.targetPlatform = diffEntity.targetPlatform
        kotlinSettingsEntity.externalSystemRunTasks = diffEntity.externalSystemRunTasks.toMutableList()
        kotlinSettingsEntity.version = diffEntity.version
        kotlinSettingsEntity.flushNeeded = diffEntity.flushNeeded
    }
}