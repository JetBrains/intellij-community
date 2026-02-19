// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.workspaceModel

import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntityBuilder
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.serialization.impl.CustomFacetRelatedEntitySerializer
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.util.descriptors.ConfigFileItemSerializer
import org.jdom.Element
import org.jetbrains.jps.model.serialization.facet.FacetState
import org.jetbrains.kotlin.config.CompilerSettings
import org.jetbrains.kotlin.config.KotlinFacetSettings
import org.jetbrains.kotlin.config.KotlinModuleKind
import org.jetbrains.kotlin.config.deserializeFacetSettings
import org.jetbrains.kotlin.config.deserializeTargetPlatformByComponentPlatforms
import org.jetbrains.kotlin.config.serializeComponentPlatforms
import org.jetbrains.kotlin.config.serializeFacetSettings
import org.jetbrains.kotlin.idea.facet.KotlinFacetType
import org.jetbrains.kotlin.platform.IdePlatformKind
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms


class KotlinModuleSettingsSerializer : CustomFacetRelatedEntitySerializer<KotlinSettingsEntity>, ConfigFileItemSerializer {
    init {
        if (!KotlinFacetBridgeFactory.kotlinFacetBridgeEnabled) {
            throw ExtensionNotApplicableException.create()
        }
    }

    override val rootEntityType: Class<KotlinSettingsEntity>
        get() = KotlinSettingsEntity::class.java
    override val supportedFacetType: String
        get() = KotlinFacetType.INSTANCE.stringId

    override fun loadEntitiesFromFacetState(
      moduleEntity: ModuleEntityBuilder,
      facetState: FacetState,
      evaluateEntitySource: (FacetState) -> EntitySource
    ) {
        val entitySource = evaluateEntitySource(facetState)
        val kotlinSettingsEntity = KotlinSettingsEntity(
            name = facetState.name, moduleId = ModuleId(moduleEntity.name),
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
            entitySource = entitySource
        ) {
            module = moduleEntity
        }

        val facetConfiguration = facetState.configuration
        if (facetConfiguration == null) {
            return
        }

        val kotlinFacetSettings = deserializeFacetSettings(facetConfiguration)

        // Can be optimized by not setting default values (KTIJ-27769)
        kotlinSettingsEntity.useProjectSettings = kotlinFacetSettings.useProjectSettings
        kotlinSettingsEntity.implementedModuleNames = kotlinFacetSettings.implementedModuleNames.toMutableList()
        kotlinSettingsEntity.dependsOnModuleNames = kotlinFacetSettings.dependsOnModuleNames.toMutableList()
        kotlinSettingsEntity.additionalVisibleModuleNames = kotlinFacetSettings.additionalVisibleModuleNames.toMutableSet()
        kotlinSettingsEntity.productionOutputPath = kotlinFacetSettings.productionOutputPath
        kotlinSettingsEntity.testOutputPath = kotlinFacetSettings.testOutputPath
        kotlinSettingsEntity.sourceSetNames = kotlinFacetSettings.sourceSetNames.toMutableList()
        kotlinSettingsEntity.isTestModule = kotlinFacetSettings.isTestModule
        kotlinSettingsEntity.targetPlatform = kotlinFacetSettings.targetPlatform?.serializeComponentPlatforms()
        kotlinSettingsEntity.externalProjectId = kotlinFacetSettings.externalProjectId
        kotlinSettingsEntity.isHmppEnabled = kotlinFacetSettings.isHmppEnabled
        kotlinSettingsEntity.pureKotlinSourceFolders = kotlinFacetSettings.pureKotlinSourceFolders.toMutableList()
        kotlinSettingsEntity.kind = kotlinFacetSettings.kind
        kotlinSettingsEntity.compilerArguments = CompilerArgumentsSerializer.serializeToString(kotlinFacetSettings.compilerArguments)
        kotlinSettingsEntity.compilerSettings = kotlinFacetSettings.compilerSettings?.let {
            CompilerSettingsData(
                it.additionalArguments,
                it.scriptTemplates,
                it.scriptTemplatesClasspath,
                it.copyJsLibraryFiles,
                it.outputDirectoryForJsLibraryFiles
            )
        }

        kotlinSettingsEntity.externalSystemRunTasks =
            kotlinFacetSettings.externalSystemRunTasks.map { it.serializeExternalSystemTestRunTask() }.toMutableList()
        kotlinSettingsEntity.flushNeeded =
            facetConfiguration.getAttributeValue("allPlatforms") != kotlinFacetSettings.targetPlatform?.serializeComponentPlatforms()
        kotlinSettingsEntity.version = kotlinFacetSettings.version
    }

    override fun serialize(entity: KotlinSettingsEntity, rootElement: Element): Element {
        KotlinFacetSettings().apply {
            version = entity.version
            useProjectSettings = entity.useProjectSettings

            compilerArguments = CompilerArgumentsSerializer.deserializeFromString(entity.compilerArguments)

            compilerSettings = entity.compilerSettings?.let {
                CompilerSettings().apply {
                    additionalArguments = it.additionalArguments
                    scriptTemplates = it.scriptTemplates
                    scriptTemplatesClasspath = it.scriptTemplatesClasspath
                    copyJsLibraryFiles = it.copyJsLibraryFiles
                    outputDirectoryForJsLibraryFiles = it.outputDirectoryForJsLibraryFiles
                }
            }

            implementedModuleNames = entity.implementedModuleNames
            dependsOnModuleNames = entity.dependsOnModuleNames
            additionalVisibleModuleNames = entity.additionalVisibleModuleNames
            productionOutputPath = entity.productionOutputPath
            testOutputPath = entity.testOutputPath
            kind = entity.kind
            sourceSetNames = entity.sourceSetNames
            isTestModule = entity.isTestModule
            externalProjectId = entity.externalProjectId
            isHmppEnabled = entity.isHmppEnabled
            pureKotlinSourceFolders = entity.pureKotlinSourceFolders
            externalSystemRunTasks = entity.externalSystemRunTasks.map { deserializeExternalSystemTestRunTask(it) }

            val args = compilerArguments
            val deserializedTargetPlatform =
                entity.targetPlatform?.deserializeTargetPlatformByComponentPlatforms()
            val singleSimplePlatform = deserializedTargetPlatform?.componentPlatforms?.singleOrNull()
            if (singleSimplePlatform == JvmPlatforms.defaultJvmPlatform.singleOrNull() && args != null) {
                targetPlatform = IdePlatformKind.platformByCompilerArguments(args)
            }
            targetPlatform = deserializedTargetPlatform
        }.serializeFacetSettings(rootElement)

        return rootElement
    }

    override fun serializeBuilder(entity: WorkspaceEntity.Builder<out KotlinSettingsEntity>, module: ModuleEntity, rootElement: Element): Element {
        entity as KotlinSettingsEntityBuilder

        KotlinFacetSettings().apply {
            version = entity.version
            useProjectSettings = entity.useProjectSettings

            compilerArguments = CompilerArgumentsSerializer.deserializeFromString(entity.compilerArguments)

            compilerSettings = entity.compilerSettings?.let {
                CompilerSettings().apply {
                    additionalArguments = it.additionalArguments
                    scriptTemplates = it.scriptTemplates
                    scriptTemplatesClasspath = it.scriptTemplatesClasspath
                    copyJsLibraryFiles = it.copyJsLibraryFiles
                    outputDirectoryForJsLibraryFiles = it.outputDirectoryForJsLibraryFiles
                }
            }

            implementedModuleNames = entity.implementedModuleNames
            dependsOnModuleNames = entity.dependsOnModuleNames
            additionalVisibleModuleNames = entity.additionalVisibleModuleNames
            productionOutputPath = entity.productionOutputPath
            testOutputPath = entity.testOutputPath
            kind = entity.kind
            sourceSetNames = entity.sourceSetNames
            isTestModule = entity.isTestModule
            externalProjectId = entity.externalProjectId
            isHmppEnabled = entity.isHmppEnabled
            pureKotlinSourceFolders = entity.pureKotlinSourceFolders
            externalSystemRunTasks = entity.externalSystemRunTasks.map { deserializeExternalSystemTestRunTask(it) }

            val args = compilerArguments
            val deserializedTargetPlatform =
                entity.targetPlatform?.deserializeTargetPlatformByComponentPlatforms()
            val singleSimplePlatform = deserializedTargetPlatform?.componentPlatforms?.singleOrNull()
            if (singleSimplePlatform == JvmPlatforms.defaultJvmPlatform.singleOrNull() && args != null) {
                targetPlatform = IdePlatformKind.platformByCompilerArguments(args)
            }
            targetPlatform = deserializedTargetPlatform
        }.serializeFacetSettings(rootElement)

        return rootElement
    }
}