// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent
import org.jetbrains.annotations.NonNls

@GeneratedCodeApiVersion(3)
interface ModifiableFacetEntity : ModifiableWorkspaceEntity<FacetEntity>, ModuleSettingsFacetBridgeEntity.Builder<FacetEntity> {
  override var entitySource: EntitySource
  override var moduleId: ModuleId
  override var name: String
  var typeId: FacetEntityTypeId
  var configurationXmlTag: String?
  var module: ModifiableModuleEntity
  var underlyingFacet: ModifiableFacetEntity?
}

internal object FacetEntityType : EntityType<FacetEntity, ModifiableFacetEntity>(ModuleSettingsFacetBridgeEntityType) {
  override val entityClass: Class<FacetEntity> get() = FacetEntity::class.java
  operator fun invoke(
    moduleId: ModuleId,
    name: String,
    typeId: FacetEntityTypeId,
    entitySource: EntitySource,
    init: (ModifiableFacetEntity.() -> Unit)? = null,
  ): ModifiableFacetEntity {
    val builder = builder()
    builder.moduleId = moduleId
    builder.name = name
    builder.typeId = typeId
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }

  @Deprecated(message = "Use new API instead")
  fun compatibilityInvoke(
    moduleId: ModuleId,
    name: String,
    typeId: FacetEntityTypeId,
    entitySource: EntitySource,
    init: (FacetEntity.Builder.() -> Unit)? = null,
  ): FacetEntity.Builder {
    val builder = builder() as FacetEntity.Builder
    builder.moduleId = moduleId
    builder.name = name
    builder.typeId = typeId
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyFacetEntity(
  entity: FacetEntity,
  modification: ModifiableFacetEntity.() -> Unit,
): FacetEntity = modifyEntity(ModifiableFacetEntity::class.java, entity, modification)

var ModifiableFacetEntity.childrenFacets: List<ModifiableFacetEntity>
  by WorkspaceEntity.extensionBuilder(FacetEntity::class.java)

@JvmOverloads
@JvmName("createFacetEntity")
fun FacetEntity(
  moduleId: ModuleId,
  name: String,
  typeId: FacetEntityTypeId,
  entitySource: EntitySource,
  init: (ModifiableFacetEntity.() -> Unit)? = null,
): ModifiableFacetEntity = FacetEntityType(moduleId, name, typeId, entitySource, init)
