// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ModifiableOoChildWithPidEntity : ModifiableWorkspaceEntity<OoChildWithPidEntity> {
  override var entitySource: EntitySource
  var childProperty: String
  var parentEntity: ModifiableOoParentWithoutPidEntity
}

internal object OoChildWithPidEntityType : EntityType<OoChildWithPidEntity, ModifiableOoChildWithPidEntity>() {
  override val entityClass: Class<OoChildWithPidEntity> get() = OoChildWithPidEntity::class.java
  operator fun invoke(
    childProperty: String,
    entitySource: EntitySource,
    init: (ModifiableOoChildWithPidEntity.() -> Unit)? = null,
  ): ModifiableOoChildWithPidEntity {
    val builder = builder()
    builder.childProperty = childProperty
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyOoChildWithPidEntity(
  entity: OoChildWithPidEntity,
  modification: ModifiableOoChildWithPidEntity.() -> Unit,
): OoChildWithPidEntity = modifyEntity(ModifiableOoChildWithPidEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createOoChildWithPidEntity")
fun OoChildWithPidEntity(
  childProperty: String,
  entitySource: EntitySource,
  init: (ModifiableOoChildWithPidEntity.() -> Unit)? = null,
): ModifiableOoChildWithPidEntity = OoChildWithPidEntityType(childProperty, entitySource, init)
