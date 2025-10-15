// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ModifiableOoChildForParentWithPidEntity : ModifiableWorkspaceEntity<OoChildForParentWithPidEntity> {
  override var entitySource: EntitySource
  var childProperty: String
  var parentEntity: ModifiableOoParentWithPidEntity
}

internal object OoChildForParentWithPidEntityType : EntityType<OoChildForParentWithPidEntity, ModifiableOoChildForParentWithPidEntity>() {
  override val entityClass: Class<OoChildForParentWithPidEntity> get() = OoChildForParentWithPidEntity::class.java
  operator fun invoke(
    childProperty: String,
    entitySource: EntitySource,
    init: (ModifiableOoChildForParentWithPidEntity.() -> Unit)? = null,
  ): ModifiableOoChildForParentWithPidEntity {
    val builder = builder()
    builder.childProperty = childProperty
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyOoChildForParentWithPidEntity(
  entity: OoChildForParentWithPidEntity,
  modification: ModifiableOoChildForParentWithPidEntity.() -> Unit,
): OoChildForParentWithPidEntity = modifyEntity(ModifiableOoChildForParentWithPidEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createOoChildForParentWithPidEntity")
fun OoChildForParentWithPidEntity(
  childProperty: String,
  entitySource: EntitySource,
  init: (ModifiableOoChildForParentWithPidEntity.() -> Unit)? = null,
): ModifiableOoChildForParentWithPidEntity = OoChildForParentWithPidEntityType(childProperty, entitySource, init)
