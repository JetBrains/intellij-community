// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ModifiableOoChildAlsoWithPidEntity : ModifiableWorkspaceEntity<OoChildAlsoWithPidEntity> {
  override var entitySource: EntitySource
  var childProperty: String
  var parentEntity: ModifiableOoParentWithPidEntity
}

internal object OoChildAlsoWithPidEntityType : EntityType<OoChildAlsoWithPidEntity, ModifiableOoChildAlsoWithPidEntity>() {
  override val entityClass: Class<OoChildAlsoWithPidEntity> get() = OoChildAlsoWithPidEntity::class.java
  operator fun invoke(
    childProperty: String,
    entitySource: EntitySource,
    init: (ModifiableOoChildAlsoWithPidEntity.() -> Unit)? = null,
  ): ModifiableOoChildAlsoWithPidEntity {
    val builder = builder()
    builder.childProperty = childProperty
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyOoChildAlsoWithPidEntity(
  entity: OoChildAlsoWithPidEntity,
  modification: ModifiableOoChildAlsoWithPidEntity.() -> Unit,
): OoChildAlsoWithPidEntity = modifyEntity(ModifiableOoChildAlsoWithPidEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createOoChildAlsoWithPidEntity")
fun OoChildAlsoWithPidEntity(
  childProperty: String,
  entitySource: EntitySource,
  init: (ModifiableOoChildAlsoWithPidEntity.() -> Unit)? = null,
): ModifiableOoChildAlsoWithPidEntity = OoChildAlsoWithPidEntityType(childProperty, entitySource, init)
