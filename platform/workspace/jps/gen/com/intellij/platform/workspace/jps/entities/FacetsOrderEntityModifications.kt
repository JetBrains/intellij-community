// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("FacetsOrderEntityModifications")

package com.intellij.platform.workspace.jps.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
@GeneratedCodeApiVersion(3)
interface FacetsOrderEntityBuilder : WorkspaceEntityBuilder<FacetsOrderEntity> {
  override var entitySource: EntitySource
  var orderOfFacets: MutableList<String>
  var moduleEntity: ModuleEntityBuilder
}

internal object FacetsOrderEntityType : EntityType<FacetsOrderEntity, FacetsOrderEntityBuilder>() {
  override val entityClass: Class<FacetsOrderEntity> get() = FacetsOrderEntity::class.java
  operator fun invoke(
    orderOfFacets: List<String>,
    entitySource: EntitySource,
    init: (FacetsOrderEntityBuilder.() -> Unit)? = null,
  ): FacetsOrderEntityBuilder {
    val builder = builder()
    builder.orderOfFacets = orderOfFacets.toMutableWorkspaceList()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }

  @Deprecated(message = "Use new API instead")
  fun compatibilityInvoke(
    orderOfFacets: List<String>,
    entitySource: EntitySource,
    init: (FacetsOrderEntity.Builder.() -> Unit)? = null,
  ): FacetsOrderEntity.Builder {
    val builder = builder() as FacetsOrderEntity.Builder
    builder.orderOfFacets = orderOfFacets.toMutableWorkspaceList()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

@Internal
fun MutableEntityStorage.modifyFacetsOrderEntity(
  entity: FacetsOrderEntity,
  modification: FacetsOrderEntityBuilder.() -> Unit,
): FacetsOrderEntity = modifyEntity(FacetsOrderEntityBuilder::class.java, entity, modification)

@Internal
@JvmOverloads
@JvmName("createFacetsOrderEntity")
fun FacetsOrderEntity(
  orderOfFacets: List<String>,
  entitySource: EntitySource,
  init: (FacetsOrderEntityBuilder.() -> Unit)? = null,
): FacetsOrderEntityBuilder = FacetsOrderEntityType(orderOfFacets, entitySource, init)
