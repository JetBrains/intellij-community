// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.entities

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls

@GeneratedCodeApiVersion(3)
interface ModifiableModuleEntity : ModifiableWorkspaceEntity<ModuleEntity> {
  override var entitySource: EntitySource
  var name: String
  var type: ModuleTypeId?
  var dependencies: MutableList<ModuleDependencyItem>
  var contentRoots: List<ModifiableContentRootEntity>
  var facets: List<ModifiableFacetEntity>
}

internal object ModuleEntityType : EntityType<ModuleEntity, ModifiableModuleEntity>() {
  override val entityClass: Class<ModuleEntity> get() = ModuleEntity::class.java
  operator fun invoke(
    name: String,
    dependencies: List<ModuleDependencyItem>,
    entitySource: EntitySource,
    init: (ModifiableModuleEntity.() -> Unit)? = null,
  ): ModifiableModuleEntity {
    val builder = builder()
    builder.name = name
    builder.dependencies = dependencies.toMutableWorkspaceList()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }

  @Deprecated(message = "Use new API instead")
  fun compatibilityInvoke(
    name: String,
    dependencies: List<ModuleDependencyItem>,
    entitySource: EntitySource,
    init: (ModuleEntity.Builder.() -> Unit)? = null,
  ): ModuleEntity.Builder {
    val builder = builder() as ModuleEntity.Builder
    builder.name = name
    builder.dependencies = dependencies.toMutableWorkspaceList()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyModuleEntity(
  entity: ModuleEntity,
  modification: ModifiableModuleEntity.() -> Unit,
): ModuleEntity = modifyEntity(ModifiableModuleEntity::class.java, entity, modification)

@get:Internal
@set:Internal
var ModifiableModuleEntity.customImlData: ModifiableModuleCustomImlDataEntity?
  by WorkspaceEntity.extensionBuilder(ModuleCustomImlDataEntity::class.java)

@get:Internal
@set:Internal
var ModifiableModuleEntity.exModuleOptions: ModifiableExternalSystemModuleOptionsEntity?
  by WorkspaceEntity.extensionBuilder(ExternalSystemModuleOptionsEntity::class.java)

@get:Internal
@set:Internal
var ModifiableModuleEntity.facetOrder: ModifiableFacetsOrderEntity?
  by WorkspaceEntity.extensionBuilder(FacetsOrderEntity::class.java)

@get:Internal
@set:Internal
var ModifiableModuleEntity.groupPath: ModifiableModuleGroupPathEntity?
  by WorkspaceEntity.extensionBuilder(ModuleGroupPathEntity::class.java)

var ModifiableModuleEntity.sourceRoots: List<ModifiableSourceRootEntity>
  by WorkspaceEntity.extensionBuilder(SourceRootEntity::class.java)

@get:Internal
@set:Internal
var ModifiableModuleEntity.testProperties: ModifiableTestModulePropertiesEntity?
  by WorkspaceEntity.extensionBuilder(TestModulePropertiesEntity::class.java)

@JvmOverloads
@JvmName("createModuleEntity")
fun ModuleEntity(
  name: String,
  dependencies: List<ModuleDependencyItem>,
  entitySource: EntitySource,
  init: (ModifiableModuleEntity.() -> Unit)? = null,
): ModifiableModuleEntity = ModuleEntityType(name, dependencies, entitySource, init)
