// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ModifiableOoParentWithoutPidEntity : ModifiableWorkspaceEntity<OoParentWithoutPidEntity> {
  override var entitySource: EntitySource
  var parentProperty: String
  var childOne: ModifiableOoChildWithPidEntity?
}

internal object OoParentWithoutPidEntityType : EntityType<OoParentWithoutPidEntity, ModifiableOoParentWithoutPidEntity>() {
  override val entityClass: Class<OoParentWithoutPidEntity> get() = OoParentWithoutPidEntity::class.java
  operator fun invoke(
    parentProperty: String,
    entitySource: EntitySource,
    init: (ModifiableOoParentWithoutPidEntity.() -> Unit)? = null,
  ): ModifiableOoParentWithoutPidEntity {
    val builder = builder()
    builder.parentProperty = parentProperty
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyOoParentWithoutPidEntity(
  entity: OoParentWithoutPidEntity,
  modification: ModifiableOoParentWithoutPidEntity.() -> Unit,
): OoParentWithoutPidEntity = modifyEntity(ModifiableOoParentWithoutPidEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createOoParentWithoutPidEntity")
fun OoParentWithoutPidEntity(
  parentProperty: String,
  entitySource: EntitySource,
  init: (ModifiableOoParentWithoutPidEntity.() -> Unit)? = null,
): ModifiableOoParentWithoutPidEntity = OoParentWithoutPidEntityType(parentProperty, entitySource, init)
