// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.workspaceModel

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.jps.entities.ModifiableModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.ModuleSettingsFacetBridgeEntity
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceSet
import com.intellij.util.descriptors.ConfigFileItem
import org.jetbrains.kotlin.config.KotlinModuleKind

@GeneratedCodeApiVersion(3)
interface ModifiableKotlinSettingsEntity : ModifiableWorkspaceEntity<KotlinSettingsEntity>, ModuleSettingsFacetBridgeEntity.Builder<KotlinSettingsEntity> {
  override var entitySource: EntitySource
  override var moduleId: ModuleId
  override var name: String
  var sourceRoots: MutableList<String>
  var configFileItems: MutableList<ConfigFileItem>
  var module: ModifiableModuleEntity
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

internal object KotlinSettingsEntityType : EntityType<KotlinSettingsEntity, ModifiableKotlinSettingsEntity>() {
  override val entityClass: Class<KotlinSettingsEntity> get() = KotlinSettingsEntity::class.java
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
    init: (ModifiableKotlinSettingsEntity.() -> Unit)? = null,
  ): ModifiableKotlinSettingsEntity {
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

  @Deprecated(message = "Use new API instead")
  fun compatibilityInvoke(
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
    init: (KotlinSettingsEntity.Builder.() -> Unit)? = null,
  ): KotlinSettingsEntity.Builder {
    val builder = builder() as KotlinSettingsEntity.Builder
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

fun MutableEntityStorage.modifyKotlinSettingsEntity(
  entity: KotlinSettingsEntity,
  modification: ModifiableKotlinSettingsEntity.() -> Unit,
): KotlinSettingsEntity = modifyEntity(ModifiableKotlinSettingsEntity::class.java, entity, modification)

var ModifiableModuleEntity.kotlinSettings: List<ModifiableKotlinSettingsEntity>
  by WorkspaceEntity.extensionBuilder(KotlinSettingsEntity::class.java)

@JvmOverloads
@JvmName("createKotlinSettingsEntity")
fun KotlinSettingsEntity(
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
  init: (ModifiableKotlinSettingsEntity.() -> Unit)? = null,
): ModifiableKotlinSettingsEntity =
  KotlinSettingsEntityType(moduleId, name, sourceRoots, configFileItems, useProjectSettings, implementedModuleNames, dependsOnModuleNames,
                           additionalVisibleModuleNames, sourceSetNames, isTestModule, externalProjectId, isHmppEnabled,
                           pureKotlinSourceFolders, kind, externalSystemRunTasks, version, flushNeeded, entitySource, init)
