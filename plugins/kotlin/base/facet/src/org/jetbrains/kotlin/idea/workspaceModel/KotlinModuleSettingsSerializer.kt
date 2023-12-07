// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.workspaceModel

import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.serialization.impl.CustomFacetRelatedEntitySerializer
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.util.descriptors.ConfigFileItemSerializer
import org.jdom.Element
import org.jetbrains.jps.model.serialization.facet.FacetState
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.config.CompilerSettings
import org.jetbrains.kotlin.idea.facet.KotlinFacetType
import org.jetbrains.kotlin.platform.IdePlatformKind
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import java.io.*
import java.util.*


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
        moduleEntity: ModuleEntity,
        facetState: FacetState,
        evaluateEntitySource: (FacetState) -> EntitySource
    ) {
        val entitySource = evaluateEntitySource(facetState)
        val kotlinSettingsEntity = KotlinSettingsEntity(
            facetState.name, moduleEntity.symbolicId,
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
            CompilerSettingsData("", "", "", true, "lib", false),
            "",
            emptyList(),
            KotlinFacetSettings.CURRENT_VERSION,
            false,
            entitySource
        ) {
            module = moduleEntity
        } as KotlinSettingsEntity.Builder

        val facetConfiguration = facetState.configuration
        if (facetConfiguration == null) {
            return
        }

        val kotlinFacetSettings = deserializeFacetSettings(facetConfiguration).also { it.updateMergedArguments() }

        // Can be optimized by not setting default values (KTIJ-27769)
        kotlinSettingsEntity.useProjectSettings = kotlinFacetSettings.useProjectSettings
        kotlinSettingsEntity.implementedModuleNames = kotlinFacetSettings.implementedModuleNames.toMutableList()
        kotlinSettingsEntity.dependsOnModuleNames = kotlinFacetSettings.dependsOnModuleNames.toMutableList()
        kotlinSettingsEntity.additionalVisibleModuleNames = kotlinFacetSettings.additionalVisibleModuleNames.toMutableSet()
        kotlinSettingsEntity.productionOutputPath = kotlinFacetSettings.productionOutputPath ?: ""
        kotlinSettingsEntity.testOutputPath = kotlinFacetSettings.testOutputPath ?: ""
        kotlinSettingsEntity.sourceSetNames = kotlinFacetSettings.sourceSetNames.toMutableList()
        kotlinSettingsEntity.isTestModule = kotlinFacetSettings.isTestModule
        kotlinSettingsEntity.targetPlatform = kotlinFacetSettings.targetPlatform?.serializeComponentPlatforms() ?: ""
        kotlinSettingsEntity.externalProjectId = kotlinFacetSettings.externalProjectId
        kotlinSettingsEntity.isHmppEnabled = kotlinFacetSettings.isHmppEnabled
        kotlinSettingsEntity.pureKotlinSourceFolders = kotlinFacetSettings.pureKotlinSourceFolders.toMutableList()
        kotlinSettingsEntity.kind = kotlinFacetSettings.kind

        if (kotlinFacetSettings.compilerArguments != null) {
            kotlinSettingsEntity.compilerArguments = serializeToString(kotlinFacetSettings.compilerArguments!!)
        }

        val compilerSettings = kotlinFacetSettings.compilerSettings
        if (compilerSettings != null) {
            kotlinSettingsEntity.compilerSettings = CompilerSettingsData(
                compilerSettings.additionalArguments,
                compilerSettings.scriptTemplates,
                compilerSettings.scriptTemplatesClasspath,
                compilerSettings.copyJsLibraryFiles,
                compilerSettings.outputDirectoryForJsLibraryFiles,
                true
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

            compilerArguments =
                if (entity.compilerArguments.isEmpty()) null else serializeFromString(entity.compilerArguments) as CommonCompilerArguments

            val compilerSettingsFromEntity = entity.compilerSettings
            val isCompilerSettingsChanged =
                CompilerSettings().let {
                    it.additionalArguments != compilerSettingsFromEntity.additionalArguments ||
                            it.scriptTemplates != compilerSettingsFromEntity.scriptTemplates ||
                            it.scriptTemplatesClasspath != compilerSettingsFromEntity.scriptTemplatesClasspath ||
                            it.copyJsLibraryFiles != compilerSettingsFromEntity.copyJsLibraryFiles ||
                            it.outputDirectoryForJsLibraryFiles != compilerSettingsFromEntity.outputDirectoryForJsLibraryFiles
                }
            compilerSettings = if (isCompilerSettingsChanged) CompilerSettings().apply {
                additionalArguments = compilerSettingsFromEntity.additionalArguments
                scriptTemplates = compilerSettingsFromEntity.scriptTemplates
                scriptTemplatesClasspath = compilerSettingsFromEntity.scriptTemplatesClasspath
                copyJsLibraryFiles = compilerSettingsFromEntity.copyJsLibraryFiles
                outputDirectoryForJsLibraryFiles = compilerSettingsFromEntity.outputDirectoryForJsLibraryFiles
            } else null

            implementedModuleNames = entity.implementedModuleNames
            dependsOnModuleNames = entity.dependsOnModuleNames
            additionalVisibleModuleNames = entity.additionalVisibleModuleNames
            productionOutputPath = entity.productionOutputPath.ifEmpty { null }
            testOutputPath = entity.testOutputPath.ifEmpty { null }
            kind = entity.kind
            sourceSetNames = entity.sourceSetNames
            isTestModule = entity.isTestModule
            externalProjectId = entity.externalProjectId
            isHmppEnabled = entity.isHmppEnabled
            pureKotlinSourceFolders = entity.pureKotlinSourceFolders
            externalSystemRunTasks = entity.externalSystemRunTasks.map { deserializeExternalSystemTestRunTask(it) }

            val args = compilerArguments
            val deserializedTargetPlatform =
                entity.targetPlatform.takeIf { it.isNotEmpty() }.deserializeTargetPlatformByComponentPlatforms()
            val singleSimplePlatform = deserializedTargetPlatform?.componentPlatforms?.singleOrNull()
            if (singleSimplePlatform == JvmPlatforms.defaultJvmPlatform.singleOrNull() && args != null) {
                targetPlatform = IdePlatformKind.platformByCompilerArguments(args)
            }
            targetPlatform = deserializedTargetPlatform
        }.serializeFacetSettings(rootElement)

        return rootElement
    }

    // naive implementation of compile arguments serialization, need to be optimized
    // TODO: optimize compiler arguments serialization (KTIJ-27769)
    companion object {
        fun serializeToString(o: Serializable?): String {
            if (o == null) return ""
            lateinit var res: String
            ByteArrayOutputStream().use { baos ->
                ObjectOutputStream(baos).use { oos ->
                    oos.writeObject(o)
                }
                res = Base64.getEncoder().encodeToString(baos.toByteArray())
            }
            return res
        }

        fun serializeFromString(s: String): Any {
            val data: ByteArray = Base64.getDecoder().decode(s)
            return ObjectInputStream(
                ByteArrayInputStream(data)
            ).use {
                it.readObject()
            }
        }
    }
}