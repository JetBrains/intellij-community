// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.workspaceModel

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntityBuilder
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.ModuleSettingsFacetBridgeEntity
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.util.descriptors.ConfigFileItem
import org.jetbrains.kotlin.config.KotlinModuleKind

interface KotlinSettingsEntity : ModuleSettingsFacetBridgeEntity {
    val sourceRoots: List<String>
    val configFileItems: List<ConfigFileItem>

    @Parent
    val module: ModuleEntity

    // trivial parameters (String, Boolean)
    val useProjectSettings: Boolean
    val implementedModuleNames: List<String>
    val dependsOnModuleNames: List<String>
    val additionalVisibleModuleNames: Set<String>
    val productionOutputPath: String?
    val testOutputPath: String?
    val sourceSetNames: List<String>
    val isTestModule: Boolean
    val externalProjectId: String
    val isHmppEnabled: Boolean
        @Deprecated(message = "Use mppVersion.isHmppEnabled", ReplaceWith("mppVersion.isHmpp"))
        get
    val pureKotlinSourceFolders: List<String>

    //semi-trivial parameters (enums)
    val kind: KotlinModuleKind

    //non-trivial parameters
    val compilerArguments: String?
    val compilerSettings: CompilerSettingsData?

    val targetPlatform: String?
    val externalSystemRunTasks: List<String>
    val version: Int
    val flushNeeded: Boolean

    override val symbolicId: KotlinSettingsId
        get() = KotlinSettingsId(name, moduleId)

    //region generated code
    @Deprecated(message = "Use KotlinSettingsEntityBuilder instead")
    interface Builder : KotlinSettingsEntityBuilder {
        @Deprecated(message = "Use new API instead")
        fun getModule(): ModuleEntity.Builder = module as ModuleEntity.Builder

        @Deprecated(message = "Use new API instead")
        fun setModule(value: ModuleEntity.Builder) {
            module = value
        }
    }

    companion object : EntityType<KotlinSettingsEntity, Builder>() {
        @Deprecated(message = "Use new API instead")
        @JvmOverloads
        @JvmStatic
        @JvmName("create")
        operator fun invoke(
            moduleId: ModuleId,
            name: String,
            sourceRoots: List<String>,
            configFileItems: List<ConfigFileItem>,
            useProjectSettings: Boolean,
            implementedModuleNames: List<String>,
            dependsOnModuleNames: List<String>,
            additionalVisibleModuleNames: Set<String>,
            sourceSetNames: List<String>,
            isTestModule: Boolean,
            externalProjectId: String,
            isHmppEnabled: Boolean,
            pureKotlinSourceFolders: List<String>,
            kind: KotlinModuleKind,
            externalSystemRunTasks: List<String>,
            version: Int,
            flushNeeded: Boolean,
            entitySource: EntitySource,
            init: (Builder.() -> Unit)? = null,
        ): Builder =
            KotlinSettingsEntityType.compatibilityInvoke(
                moduleId, name, sourceRoots, configFileItems, useProjectSettings, implementedModuleNames,
                dependsOnModuleNames, additionalVisibleModuleNames, sourceSetNames, isTestModule,
                externalProjectId, isHmppEnabled, pureKotlinSourceFolders, kind, externalSystemRunTasks,
                version, flushNeeded, entitySource, init
            )

        @Deprecated(message = "Use new API instead")
        @JvmOverloads
        @JvmStatic
        @JvmName("create")
        fun create(
            name: String,
            moduleId: ModuleId,
            sourceRoots: List<String>,
            configFileItems: List<ConfigFileItem>,
            useProjectSettings: Boolean,
            implementedModuleNames: List<String>,
            dependsOnModuleNames: List<String>,
            additionalVisibleModuleNames: Set<String>,
            sourceSetNames: List<String>,
            isTestModule: Boolean,
            externalProjectId: String,
            isHmppEnabled: Boolean,
            pureKotlinSourceFolders: List<String>,
            kind: KotlinModuleKind,
            externalSystemRunTasks: List<String>,
            version: Int,
            flushNeeded: Boolean,
            entitySource: EntitySource,
            init: (Builder.() -> Unit)? = null,
        ): Builder = KotlinSettingsEntityType.compatibilityInvoke(
            moduleId, name, sourceRoots, configFileItems, useProjectSettings, implementedModuleNames, dependsOnModuleNames,
            additionalVisibleModuleNames, sourceSetNames, isTestModule, externalProjectId, isHmppEnabled,
            pureKotlinSourceFolders, kind, externalSystemRunTasks, version, flushNeeded, entitySource, init
        )
        //endregion compatibility generated code
    }
    //endregion
}

//region generated code
@Deprecated(message = "Use new API instead")
fun MutableEntityStorage.modifyKotlinSettingsEntity(
    entity: KotlinSettingsEntity,
    modification: KotlinSettingsEntity.Builder.() -> Unit,
): KotlinSettingsEntity {
    return modifyEntity(KotlinSettingsEntity.Builder::class.java, entity, modification)
}

@Deprecated(message = "Use new API instead")
var ModuleEntity.Builder.kotlinSettings: List<KotlinSettingsEntity.Builder>
    get() = (this as ModuleEntityBuilder).kotlinSettings as List<KotlinSettingsEntity.Builder>
    set(value) {
        (this as ModuleEntityBuilder).kotlinSettings = value
    }
//endregion

data class CompilerSettingsData(
    val additionalArguments: String,
    val scriptTemplates: String,
    val scriptTemplatesClasspath: String,
    val copyJsLibraryFiles: Boolean,
    val outputDirectoryForJsLibraryFiles: String
)

val ModuleEntity.kotlinSettings: List<KotlinSettingsEntity>
        by WorkspaceEntity.extension()

data class KotlinSettingsId(val name: @NlsSafe String, val parentId: ModuleId) : SymbolicEntityId<KotlinSettingsEntity> {
    override val presentableName: String
        get() = name
}