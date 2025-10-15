// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ModifiableOoChildEntity : ModifiableWorkspaceEntity<OoChildEntity> {
  override var entitySource: EntitySource
  var childProperty: String
  var parentEntity: ModifiableOoParentEntity
}

internal object OoChildEntityType : EntityType<OoChildEntity, ModifiableOoChildEntity>() {
  override val entityClass: Class<OoChildEntity> get() = OoChildEntity::class.java
  operator fun invoke(
    childProperty: String,
    entitySource: EntitySource,
    init: (ModifiableOoChildEntity.() -> Unit)? = null,
  ): ModifiableOoChildEntity {
    val builder = builder()
    builder.childProperty = childProperty
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyOoChildEntity(
  entity: OoChildEntity,
  modification: ModifiableOoChildEntity.() -> Unit,
): OoChildEntity = modifyEntity(ModifiableOoChildEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createOoChildEntity")
fun OoChildEntity(
  childProperty: String,
  entitySource: EntitySource,
  init: (ModifiableOoChildEntity.() -> Unit)? = null,
): ModifiableOoChildEntity = OoChildEntityType(childProperty, entitySource, init)
