// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ModuleEntityModifications")

package com.intellij.platform.workspace.jps.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import org.jetbrains.annotations.ApiStatus.Internal

@GeneratedCodeApiVersion(3)
interface ModuleEntityBuilder : WorkspaceEntityBuilder<ModuleEntity> {
  override var entitySource: EntitySource
  var name: String
  var type: ModuleTypeId?
  var dependencies: MutableList<ModuleDependencyItem>
  var contentRoots: List<ContentRootEntityBuilder>
  var facets: List<FacetEntityBuilder>
}

internal object ModuleEntityType : EntityType<ModuleEntity, ModuleEntityBuilder>() {
  override val entityClass: Class<ModuleEntity> get() = ModuleEntity::class.java
  operator fun invoke(
    name: String,
    dependencies: List<ModuleDependencyItem>,
    entitySource: EntitySource,
    init: (ModuleEntityBuilder.() -> Unit)? = null,
  ): ModuleEntityBuilder {
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
  modification: ModuleEntityBuilder.() -> Unit,
): ModuleEntity = modifyEntity(ModuleEntityBuilder::class.java, entity, modification)

@get:Internal
@set:Internal
var ModuleEntityBuilder.customImlData: ModuleCustomImlDataEntityBuilder?
  by WorkspaceEntity.extensionBuilder(ModuleCustomImlDataEntity::class.java)

@get:Internal
@set:Internal
var ModuleEntityBuilder.exModuleOptions: ExternalSystemModuleOptionsEntityBuilder?
  by WorkspaceEntity.extensionBuilder(ExternalSystemModuleOptionsEntity::class.java)

@get:Internal
@set:Internal
var ModuleEntityBuilder.facetOrder: FacetsOrderEntityBuilder?
  by WorkspaceEntity.extensionBuilder(FacetsOrderEntity::class.java)

@get:Internal
@set:Internal
var ModuleEntityBuilder.groupPath: ModuleGroupPathEntityBuilder?
  by WorkspaceEntity.extensionBuilder(ModuleGroupPathEntity::class.java)

var ModuleEntityBuilder.sourceRoots: List<SourceRootEntityBuilder>
  by WorkspaceEntity.extensionBuilder(SourceRootEntity::class.java)

@get:Internal
@set:Internal
var ModuleEntityBuilder.testProperties: TestModulePropertiesEntityBuilder?
  by WorkspaceEntity.extensionBuilder(TestModulePropertiesEntity::class.java)

@JvmOverloads
@JvmName("createModuleEntity")
fun ModuleEntity(
  name: String,
  dependencies: List<ModuleDependencyItem>,
  entitySource: EntitySource,
  init: (ModuleEntityBuilder.() -> Unit)? = null,
): ModuleEntityBuilder = ModuleEntityType(name, dependencies, entitySource, init)
