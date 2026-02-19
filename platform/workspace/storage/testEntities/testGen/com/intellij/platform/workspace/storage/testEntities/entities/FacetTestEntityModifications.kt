// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("FacetTestEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*

@GeneratedCodeApiVersion(3)
interface FacetTestEntityBuilder : WorkspaceEntityBuilder<FacetTestEntity> {
  override var entitySource: EntitySource
  var data: String
  var moreData: String
  var module: ModuleTestEntityBuilder
}

internal object FacetTestEntityType : EntityType<FacetTestEntity, FacetTestEntityBuilder>() {
  override val entityClass: Class<FacetTestEntity> get() = FacetTestEntity::class.java
  operator fun invoke(
    data: String,
    moreData: String,
    entitySource: EntitySource,
    init: (FacetTestEntityBuilder.() -> Unit)? = null,
  ): FacetTestEntityBuilder {
    val builder = builder()
    builder.data = data
    builder.moreData = moreData
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyFacetTestEntity(
  entity: FacetTestEntity,
  modification: FacetTestEntityBuilder.() -> Unit,
): FacetTestEntity = modifyEntity(FacetTestEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createFacetTestEntity")
fun FacetTestEntity(
  data: String,
  moreData: String,
  entitySource: EntitySource,
  init: (FacetTestEntityBuilder.() -> Unit)? = null,
): FacetTestEntityBuilder = FacetTestEntityType(data, moreData, entitySource, init)
