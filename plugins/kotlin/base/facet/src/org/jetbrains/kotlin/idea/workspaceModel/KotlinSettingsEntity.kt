// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.workspaceModel

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.ModuleSettingsBase
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceSet
import com.intellij.util.descriptors.ConfigFileItem
import org.jetbrains.kotlin.config.KotlinModuleKind

interface KotlinSettingsEntity : ModuleSettingsBase {
    val sourceRoots: List<String>
    val configFileItems: List<ConfigFileItem>

    val module: ModuleEntity

    // trivial parameters (String, Boolean)
    val useProjectSettings: Boolean
    val implementedModuleNames: List<String>
    val dependsOnModuleNames: List<String>
    val additionalVisibleModuleNames: Set<String>
    val productionOutputPath: String
    val testOutputPath: String
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
    val compilerArguments: String
    val compilerSettings: CompilerSettingsData

    val targetPlatform: String
    val externalSystemRunTasks: List<String>
    val version: Int
    val flushNeeded: Boolean

    override val symbolicId: KotlinSettingsId
        get() = KotlinSettingsId(name, moduleId)

    //region generated code
    @GeneratedCodeApiVersion(2)
    interface Builder : KotlinSettingsEntity, ModuleSettingsBase.Builder<KotlinSettingsEntity>,
                        WorkspaceEntity.Builder<KotlinSettingsEntity> {
        override var entitySource: EntitySource
        override var name: String
        override var moduleId: ModuleId
        override var sourceRoots: MutableList<String>
        override var configFileItems: MutableList<ConfigFileItem>
        override var module: ModuleEntity
        override var useProjectSettings: Boolean
        override var implementedModuleNames: MutableList<String>
        override var dependsOnModuleNames: MutableList<String>
        override var additionalVisibleModuleNames: MutableSet<String>
        override var productionOutputPath: String
        override var testOutputPath: String
        override var sourceSetNames: MutableList<String>
        override var isTestModule: Boolean
        override var externalProjectId: String
        override var isHmppEnabled: Boolean
        override var pureKotlinSourceFolders: MutableList<String>
        override var kind: KotlinModuleKind
        override var compilerArguments: String
        override var compilerSettings: CompilerSettingsData
        override var targetPlatform: String
        override var externalSystemRunTasks: MutableList<String>
        override var version: Int
        override var flushNeeded: Boolean
    }

    companion object : EntityType<KotlinSettingsEntity, Builder>(ModuleSettingsBase) {
        @JvmOverloads
        @JvmStatic
        @JvmName("create")
        operator fun invoke(
            name: String,
            moduleId: ModuleId,
            sourceRoots: List<String>,
            configFileItems: List<ConfigFileItem>,
            useProjectSettings: Boolean,
            implementedModuleNames: List<String>,
            dependsOnModuleNames: List<String>,
            additionalVisibleModuleNames: Set<String>,
            productionOutputPath: String,
            testOutputPath: String,
            sourceSetNames: List<String>,
            isTestModule: Boolean,
            externalProjectId: String,
            isHmppEnabled: Boolean,
            pureKotlinSourceFolders: List<String>,
            kind: KotlinModuleKind,
            compilerArguments: String,
            compilerSettings: CompilerSettingsData,
            targetPlatform: String,
            externalSystemRunTasks: List<String>,
            version: Int,
            flushNeeded: Boolean,
            entitySource: EntitySource,
            init: (Builder.() -> Unit)? = null
        ): KotlinSettingsEntity {
            val builder = builder()
            builder.name = name
            builder.moduleId = moduleId
            builder.sourceRoots = sourceRoots.toMutableWorkspaceList()
            builder.configFileItems = configFileItems.toMutableWorkspaceList()
            builder.useProjectSettings = useProjectSettings
            builder.implementedModuleNames = implementedModuleNames.toMutableWorkspaceList()
            builder.dependsOnModuleNames = dependsOnModuleNames.toMutableWorkspaceList()
            builder.additionalVisibleModuleNames = additionalVisibleModuleNames.toMutableWorkspaceSet()
            builder.productionOutputPath = productionOutputPath
            builder.testOutputPath = testOutputPath
            builder.sourceSetNames = sourceSetNames.toMutableWorkspaceList()
            builder.isTestModule = isTestModule
            builder.externalProjectId = externalProjectId
            builder.isHmppEnabled = isHmppEnabled
            builder.pureKotlinSourceFolders = pureKotlinSourceFolders.toMutableWorkspaceList()
            builder.kind = kind
            builder.compilerArguments = compilerArguments
            builder.compilerSettings = compilerSettings
            builder.targetPlatform = targetPlatform
            builder.externalSystemRunTasks = externalSystemRunTasks.toMutableWorkspaceList()
            builder.version = version
            builder.flushNeeded = flushNeeded
            builder.entitySource = entitySource
            init?.invoke(builder)
            return builder
        }
    }
    //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(
    entity: KotlinSettingsEntity,
    modification: KotlinSettingsEntity.Builder.() -> Unit
): KotlinSettingsEntity = modifyEntity(KotlinSettingsEntity.Builder::class.java, entity, modification)

var ModuleEntity.Builder.kotlinSettings: @Child List<KotlinSettingsEntity>
        by WorkspaceEntity.extension()
//endregion


data class CompilerSettingsData(
    val additionalArguments: String,
    val scriptTemplates: String,
    val scriptTemplatesClasspath: String,
    val copyJsLibraryFiles: Boolean,
    val outputDirectoryForJsLibraryFiles: String,
    val isInitialized: Boolean
)

val ModuleEntity.kotlinSettings: List<@Child KotlinSettingsEntity>
        by WorkspaceEntity.extension()

data class KotlinSettingsId(val name: @NlsSafe String, val parentId: ModuleId) : SymbolicEntityId<KotlinSettingsEntity> {
    override val presentableName: String
        get() = name
}
