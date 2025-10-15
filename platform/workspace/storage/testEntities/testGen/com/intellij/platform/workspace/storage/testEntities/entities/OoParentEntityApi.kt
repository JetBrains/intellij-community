// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ModifiableOoParentEntity : ModifiableWorkspaceEntity<OoParentEntity> {
  override var entitySource: EntitySource
  var parentProperty: String
  var child: ModifiableOoChildEntity?
  var anotherChild: ModifiableOoChildWithNullableParentEntity?
}

internal object OoParentEntityType : EntityType<OoParentEntity, ModifiableOoParentEntity>() {
  override val entityClass: Class<OoParentEntity> get() = OoParentEntity::class.java
  operator fun invoke(
    parentProperty: String,
    entitySource: EntitySource,
    init: (ModifiableOoParentEntity.() -> Unit)? = null,
  ): ModifiableOoParentEntity {
    val builder = builder()
    builder.parentProperty = parentProperty
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyOoParentEntity(
  entity: OoParentEntity,
  modification: ModifiableOoParentEntity.() -> Unit,
): OoParentEntity = modifyEntity(ModifiableOoParentEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createOoParentEntity")
fun OoParentEntity(
  parentProperty: String,
  entitySource: EntitySource,
  init: (ModifiableOoParentEntity.() -> Unit)? = null,
): ModifiableOoParentEntity = OoParentEntityType(parentProperty, entitySource, init)
