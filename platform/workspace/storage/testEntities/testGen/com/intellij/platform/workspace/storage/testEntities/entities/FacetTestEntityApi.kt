// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ModifiableFacetTestEntity : ModifiableWorkspaceEntity<FacetTestEntity> {
  override var entitySource: EntitySource
  var data: String
  var moreData: String
  var module: ModifiableModuleTestEntity
}

internal object FacetTestEntityType : EntityType<FacetTestEntity, ModifiableFacetTestEntity>() {
  override val entityClass: Class<FacetTestEntity> get() = FacetTestEntity::class.java
  operator fun invoke(
    data: String,
    moreData: String,
    entitySource: EntitySource,
    init: (ModifiableFacetTestEntity.() -> Unit)? = null,
  ): ModifiableFacetTestEntity {
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
  modification: ModifiableFacetTestEntity.() -> Unit,
): FacetTestEntity = modifyEntity(ModifiableFacetTestEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createFacetTestEntity")
fun FacetTestEntity(
  data: String,
  moreData: String,
  entitySource: EntitySource,
  init: (ModifiableFacetTestEntity.() -> Unit)? = null,
): ModifiableFacetTestEntity = FacetTestEntityType(data, moreData, entitySource, init)
