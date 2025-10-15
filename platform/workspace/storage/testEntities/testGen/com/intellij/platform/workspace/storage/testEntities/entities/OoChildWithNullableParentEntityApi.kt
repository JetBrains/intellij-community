// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ModifiableOoChildWithNullableParentEntity : ModifiableWorkspaceEntity<OoChildWithNullableParentEntity> {
  override var entitySource: EntitySource
  var parentEntity: ModifiableOoParentEntity?
}

internal object OoChildWithNullableParentEntityType : EntityType<OoChildWithNullableParentEntity, ModifiableOoChildWithNullableParentEntity>() {
  override val entityClass: Class<OoChildWithNullableParentEntity> get() = OoChildWithNullableParentEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (ModifiableOoChildWithNullableParentEntity.() -> Unit)? = null,
  ): ModifiableOoChildWithNullableParentEntity {
    val builder = builder()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyOoChildWithNullableParentEntity(
  entity: OoChildWithNullableParentEntity,
  modification: ModifiableOoChildWithNullableParentEntity.() -> Unit,
): OoChildWithNullableParentEntity = modifyEntity(ModifiableOoChildWithNullableParentEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createOoChildWithNullableParentEntity")
fun OoChildWithNullableParentEntity(
  entitySource: EntitySource,
  init: (ModifiableOoChildWithNullableParentEntity.() -> Unit)? = null,
): ModifiableOoChildWithNullableParentEntity = OoChildWithNullableParentEntityType(entitySource, init)
