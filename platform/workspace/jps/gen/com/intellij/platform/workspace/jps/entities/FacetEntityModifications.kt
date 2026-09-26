// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("FacetEntityModifications")

package com.intellij.platform.workspace.jps.entities

import com.intellij.platform.workspace.jps.entities.impl.FacetEntityImpl
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder

@GeneratedCodeApiVersion(3)
interface FacetEntityBuilder : WorkspaceEntityBuilder<FacetEntity>, ModuleSettingsFacetBridgeEntity.Builder<FacetEntity> {
  override var entitySource: EntitySource
  override var name: String
  override var module: ModuleEntityBuilder
  var typeId: FacetEntityTypeId
  var configurationXmlTag: String?
  var underlyingFacet: FacetEntityBuilder?
}

internal object FacetEntityType : EntityType<FacetEntity, FacetEntityBuilder>() {
  override val entityImplClass: Class<*> get() = FacetEntityImpl::class.java
  override val entityImplBuilderClass: Class<*> get() = FacetEntityImpl.Builder::class.java
  operator fun invoke(
    name: String,
    typeId: FacetEntityTypeId,
    entitySource: EntitySource,
    init: (FacetEntityBuilder.() -> Unit)? = null,
  ): FacetEntityBuilder {
    val builder = builder()
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
    builder.name = name
    builder.typeId = typeId
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyFacetEntity(
  entity: FacetEntity,
  modification: FacetEntityBuilder.() -> Unit,
): FacetEntity = modifyEntity(FacetEntityBuilder::class.java, entity, modification)

var FacetEntityBuilder.childrenFacets: List<FacetEntityBuilder>
  by WorkspaceEntity.extensionBuilder(FacetEntity::class.java)

// IJPL-150365
@Deprecated(message = "Use new constructor without moduleId")
@JvmOverloads
@JvmName("createFacetEntity")
fun FacetEntity(
  moduleId: ModuleId,
  name: String,
  typeId: FacetEntityTypeId,
  entitySource: EntitySource,
  init: (FacetEntityBuilder.() -> Unit)? = null,
): FacetEntityBuilder = FacetEntityType(name, typeId, entitySource, init)

@JvmOverloads
@JvmName("createFacetEntity")
fun FacetEntity(
  name: String,
  typeId: FacetEntityTypeId,
  entitySource: EntitySource,
  init: (FacetEntityBuilder.() -> Unit)? = null,
): FacetEntityBuilder = FacetEntityType(name, typeId, entitySource, init)
