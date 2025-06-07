// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.workspaceModel

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.ModuleSettingsFacetBridgeEntity
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

interface KotlinSettingsEntity : ModuleSettingsFacetBridgeEntity {
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
    val compilerArguments: String?
    val compilerSettings: CompilerSettingsData?

    val targetPlatform: String?
    val externalSystemRunTasks: List<String>
    val version: Int
    val flushNeeded: Boolean

    override val symbolicId: KotlinSettingsId
        get() = KotlinSettingsId(name, moduleId)

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<KotlinSettingsEntity>, ModuleSettingsFacetBridgeEntity.Builder<KotlinSettingsEntity> {
    override var entitySource: EntitySource
    override var moduleId: ModuleId
    override var name: String
    var sourceRoots: MutableList<String>
    var configFileItems: MutableList<ConfigFileItem>
    var module: ModuleEntity.Builder
    var useProjectSettings: Boolean
    var implementedModuleNames: MutableList<String>
    var dependsOnModuleNames: MutableList<String>
    var additionalVisibleModuleNames: MutableSet<String>
    var productionOutputPath: String?
    var testOutputPath: String?
    var sourceSetNames: MutableList<String>
    var isTestModule: Boolean
    var externalProjectId: String
    var isHmppEnabled: Boolean
    var pureKotlinSourceFolders: MutableList<String>
    var kind: KotlinModuleKind
    var compilerArguments: String?
    var compilerSettings: CompilerSettingsData?
    var targetPlatform: String?
    var externalSystemRunTasks: MutableList<String>
    var version: Int
    var flushNeeded: Boolean
  }

  companion object : EntityType<KotlinSettingsEntity, Builder>(ModuleSettingsFacetBridgeEntity) {
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
    ): Builder {
      val builder = builder()
      builder.moduleId = moduleId
      builder.name = name
      builder.sourceRoots = sourceRoots.toMutableWorkspaceList()
      builder.configFileItems = configFileItems.toMutableWorkspaceList()
      builder.useProjectSettings = useProjectSettings
      builder.implementedModuleNames = implementedModuleNames.toMutableWorkspaceList()
      builder.dependsOnModuleNames = dependsOnModuleNames.toMutableWorkspaceList()
      builder.additionalVisibleModuleNames = additionalVisibleModuleNames.toMutableWorkspaceSet()
      builder.sourceSetNames = sourceSetNames.toMutableWorkspaceList()
      builder.isTestModule = isTestModule
      builder.externalProjectId = externalProjectId
      builder.isHmppEnabled = isHmppEnabled
      builder.pureKotlinSourceFolders = pureKotlinSourceFolders.toMutableWorkspaceList()
      builder.kind = kind
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
fun MutableEntityStorage.modifyKotlinSettingsEntity(
  entity: KotlinSettingsEntity,
  modification: KotlinSettingsEntity.Builder.() -> Unit,
): KotlinSettingsEntity {
  return modifyEntity(KotlinSettingsEntity.Builder::class.java, entity, modification)
}

var ModuleEntity.Builder.kotlinSettings: @Child List<KotlinSettingsEntity.Builder>
  by WorkspaceEntity.extensionBuilder(KotlinSettingsEntity::class.java)
//endregion

data class CompilerSettingsData(
    val additionalArguments: String,
    val scriptTemplates: String,
    val scriptTemplatesClasspath: String,
    val copyJsLibraryFiles: Boolean,
    val outputDirectoryForJsLibraryFiles: String
)

val ModuleEntity.kotlinSettings: List<@Child KotlinSettingsEntity>
        by WorkspaceEntity.extension()

data class KotlinSettingsId(val name: @NlsSafe String, val parentId: ModuleId) : SymbolicEntityId<KotlinSettingsEntity> {
    override val presentableName: String
        get() = name
}