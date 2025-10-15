// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ModifiableOoParentWithPidEntity : ModifiableWorkspaceEntity<OoParentWithPidEntity> {
  override var entitySource: EntitySource
  var parentProperty: String
  var childOne: ModifiableOoChildForParentWithPidEntity?
  var childThree: ModifiableOoChildAlsoWithPidEntity?
}

internal object OoParentWithPidEntityType : EntityType<OoParentWithPidEntity, ModifiableOoParentWithPidEntity>() {
  override val entityClass: Class<OoParentWithPidEntity> get() = OoParentWithPidEntity::class.java
  operator fun invoke(
    parentProperty: String,
    entitySource: EntitySource,
    init: (ModifiableOoParentWithPidEntity.() -> Unit)? = null,
  ): ModifiableOoParentWithPidEntity {
    val builder = builder()
    builder.parentProperty = parentProperty
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyOoParentWithPidEntity(
  entity: OoParentWithPidEntity,
  modification: ModifiableOoParentWithPidEntity.() -> Unit,
): OoParentWithPidEntity = modifyEntity(ModifiableOoParentWithPidEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createOoParentWithPidEntity")
fun OoParentWithPidEntity(
  parentProperty: String,
  entitySource: EntitySource,
  init: (ModifiableOoParentWithPidEntity.() -> Unit)? = null,
): ModifiableOoParentWithPidEntity = OoParentWithPidEntityType(parentProperty, entitySource, init)
