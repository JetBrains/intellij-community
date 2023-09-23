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
        val kotlinSettingsEntity = KotlinSettingsEntity(facetState.name, moduleEntity.symbolicId,
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
                                                     entitySource) {
            module = moduleEntity
        } as KotlinSettingsEntity.Builder

        val facetConfiguration = facetState.configuration
        if (facetConfiguration == null) {
            return
        }

        val kotlinFacetSettings = deserializeFacetSettings(facetConfiguration).also { it.updateMergedArguments() }

        // Can be optimized by not setting default values
        kotlinSettingsEntity.useProjectSettings = kotlinFacetSettings.useProjectSettings
        kotlinSettingsEntity.implementedModuleNames = kotlinFacetSettings.implementedModuleNames.toMutableList()
        kotlinSettingsEntity.dependsOnModuleNames = kotlinFacetSettings.dependsOnModuleNames.toMutableList()
        kotlinSettingsEntity.additionalVisibleModuleNames = kotlinFacetSettings.additionalVisibleModuleNames.toMutableSet()
        kotlinSettingsEntity.productionOutputPath = kotlinFacetSettings.productionOutputPath ?: ""
        kotlinSettingsEntity.testOutputPath = kotlinFacetSettings.testOutputPath ?: ""
        kotlinSettingsEntity.sourceSetNames = kotlinFacetSettings.sourceSetNames.toMutableList()
        kotlinSettingsEntity.isTestModule = kotlinFacetSettings.isTestModule
        kotlinSettingsEntity.externalProjectId = kotlinFacetSettings.externalProjectId
        kotlinSettingsEntity.isHmppEnabled = kotlinFacetSettings.isHmppEnabled
        kotlinSettingsEntity.pureKotlinSourceFolders = kotlinFacetSettings.pureKotlinSourceFolders.toMutableList()
        kotlinSettingsEntity.kind = kotlinFacetSettings.kind

        if (kotlinFacetSettings.mergedCompilerArguments != null) {
            kotlinSettingsEntity.mergedCompilerArguments = serializeToString(kotlinFacetSettings.mergedCompilerArguments!!)
        }

        if (kotlinFacetSettings.compilerArguments != null) {
            kotlinSettingsEntity.compilerArguments = serializeToString(kotlinFacetSettings.compilerArguments!!)
        }

        val compilerSettings = kotlinFacetSettings.compilerSettings
        if (compilerSettings != null) {
            kotlinSettingsEntity.compilerSettings = CompilerSettings(
                compilerSettings.additionalArguments,
                compilerSettings.scriptTemplates,
                compilerSettings.scriptTemplatesClasspath,
                compilerSettings.copyJsLibraryFiles,
                compilerSettings.outputDirectoryForJsLibraryFiles
            )
        }

    }

    override fun serialize(entity: KotlinSettingsEntity, rootElement: Element): Element {
        // TODO: optimize compiler arguments serialization
        KotlinFacetSettings().apply {
            useProjectSettings = entity.useProjectSettings

            //mergedCompilerArguments =
            //    if (entity.mergedCompilerArguments.isEmpty()) null else serializeFromString(entity.mergedCompilerArguments) as CommonCompilerArguments
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

            //externalSystemRunTasks = entity.externalSystemRunTasks

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
        }.serializeFacetSettings(rootElement)

        return rootElement
    }

    // naive implementation of compile arguments serialization, need to be optimized
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