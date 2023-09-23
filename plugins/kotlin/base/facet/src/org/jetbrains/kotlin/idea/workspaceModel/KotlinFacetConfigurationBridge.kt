// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.workspaceModel

import com.intellij.facet.ui.FacetEditorContext
import com.intellij.facet.ui.FacetEditorTab
import com.intellij.facet.ui.FacetValidatorsManager
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.workspaceModel.ide.impl.legacyBridge.facet.FacetConfigurationBridge
import org.jetbrains.kotlin.config.IKotlinFacetSettings
import org.jetbrains.kotlin.config.KotlinModuleKind
import org.jetbrains.kotlin.idea.facet.KotlinFacetConfiguration
import org.jetbrains.kotlin.idea.facet.KotlinFacetType
import org.jetbrains.kotlin.idea.serialization.KotlinFacetSettingsWorkspaceModel

class KotlinFacetConfigurationBridge : KotlinFacetConfiguration, FacetConfigurationBridge<KotlinSettingsEntity> {
    override var settings: IKotlinFacetSettings
        private set

    private val kotlinSettingsEntity: KotlinSettingsEntity.Builder
    private var myModule: ModuleEntity? = null

    private constructor(kotlinSettingsEntity: KotlinSettingsEntity.Builder) :
            super() {
        this.kotlinSettingsEntity = kotlinSettingsEntity
        settings = KotlinFacetSettingsWorkspaceModel(kotlinSettingsEntity)
    }

    constructor() :
            this(
                KotlinSettingsEntity(KotlinFacetType.INSTANCE.presentableName,
                                     ModuleId(""),
                                     emptyList(),
                                     emptyList(),
                                     true,
                                     emptyList(),
                                     emptyList(),
                                     emptySet(),
                                     "",
                                     "",
                                     emptyList(),
                                     false,
                                     "",
                                     false,
                                     emptyList(),
                                     KotlinModuleKind.DEFAULT,
                                     "",
                                     "",
                                     CompilerSettings("", "", "", true, "lib"),
                                     "",
                                     object : EntitySource {}) as KotlinSettingsEntity.Builder
            )

    constructor(originKotlinSettingsEntity: KotlinSettingsEntity) :
            this(KotlinSettingsEntity(
                originKotlinSettingsEntity.name,
                originKotlinSettingsEntity.moduleId,
                originKotlinSettingsEntity.sourceRoots,
                originKotlinSettingsEntity.configFileItems,
                originKotlinSettingsEntity.useProjectSettings,
                originKotlinSettingsEntity.implementedModuleNames,
                originKotlinSettingsEntity.dependsOnModuleNames,
                originKotlinSettingsEntity.additionalVisibleModuleNames,
                originKotlinSettingsEntity.productionOutputPath,
                originKotlinSettingsEntity.testOutputPath,
                originKotlinSettingsEntity.sourceSetNames,
                originKotlinSettingsEntity.isTestModule,
                originKotlinSettingsEntity.externalProjectId,
                originKotlinSettingsEntity.isHmppEnabled,
                originKotlinSettingsEntity.pureKotlinSourceFolders,
                originKotlinSettingsEntity.kind,
                originKotlinSettingsEntity.mergedCompilerArguments,
                originKotlinSettingsEntity.compilerArguments,
                originKotlinSettingsEntity.compilerSettings,
                originKotlinSettingsEntity.targetPlatform,
                originKotlinSettingsEntity.entitySource
            ) {
            } as KotlinSettingsEntity.Builder) {
        myModule = originKotlinSettingsEntity.module
        //addConfigFileItems(originKotlinSettingsEntity.configFileItems)
    }

    override fun createEditorTabs(editorContext: FacetEditorContext?, validatorsManager: FacetValidatorsManager?): Array<FacetEditorTab> {
        TODO("Not yet implemented")
    }

    override fun init(moduleEntity: ModuleEntity, entitySource: EntitySource) {
        myModule = moduleEntity
        kotlinSettingsEntity.moduleId = moduleEntity.symbolicId
        kotlinSettingsEntity.entitySource = entitySource
    }

    override fun rename(newName: String) {
        kotlinSettingsEntity.name = newName
    }

    override fun getEntity(moduleEntity: ModuleEntity): KotlinSettingsEntity {
        return KotlinSettingsEntity(
            kotlinSettingsEntity.name,
            kotlinSettingsEntity.moduleId,
            kotlinSettingsEntity.sourceRoots,
            kotlinSettingsEntity.configFileItems,
            kotlinSettingsEntity.useProjectSettings,
            kotlinSettingsEntity.implementedModuleNames,
            kotlinSettingsEntity.dependsOnModuleNames,
            kotlinSettingsEntity.additionalVisibleModuleNames,
            kotlinSettingsEntity.productionOutputPath,
            kotlinSettingsEntity.testOutputPath,
            kotlinSettingsEntity.sourceSetNames,
            kotlinSettingsEntity.isTestModule,
            kotlinSettingsEntity.externalProjectId,
            kotlinSettingsEntity.isHmppEnabled,
            kotlinSettingsEntity.pureKotlinSourceFolders,
            kotlinSettingsEntity.kind,
            kotlinSettingsEntity.mergedCompilerArguments,
            kotlinSettingsEntity.compilerArguments,
            kotlinSettingsEntity.compilerSettings,
            kotlinSettingsEntity.targetPlatform,
            kotlinSettingsEntity.entitySource,
        ) {
            module = moduleEntity
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
        kotlinSettingsEntity.mergedCompilerArguments = diffEntity.mergedCompilerArguments
        kotlinSettingsEntity.compilerArguments = diffEntity.compilerArguments
        kotlinSettingsEntity.compilerSettings = diffEntity.compilerSettings
        kotlinSettingsEntity.targetPlatform = diffEntity.targetPlatform
    }
}
