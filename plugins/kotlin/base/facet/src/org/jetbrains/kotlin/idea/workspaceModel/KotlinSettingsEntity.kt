// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
    val compilerArguments: String
    val compilerSettings: CompilerSettingsData

    val targetPlatform: String
    val externalSystemRunTasks: List<String>
    val version: Int
    val flushNeeded: Boolean

    override val symbolicId: KotlinSettingsId
        get() = KotlinSettingsId(name, moduleId)
}

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
