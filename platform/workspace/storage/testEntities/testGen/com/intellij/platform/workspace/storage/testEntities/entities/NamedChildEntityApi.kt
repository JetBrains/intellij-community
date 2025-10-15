// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList

@GeneratedCodeApiVersion(3)
interface ModifiableNamedChildEntity : ModifiableWorkspaceEntity<NamedChildEntity> {
  override var entitySource: EntitySource
  var childProperty: String
  var parentEntity: ModifiableNamedEntity
}

internal object NamedChildEntityType : EntityType<NamedChildEntity, ModifiableNamedChildEntity>() {
  override val entityClass: Class<NamedChildEntity> get() = NamedChildEntity::class.java
  operator fun invoke(
    childProperty: String,
    entitySource: EntitySource,
    init: (ModifiableNamedChildEntity.() -> Unit)? = null,
  ): ModifiableNamedChildEntity {
    val builder = builder()
    builder.childProperty = childProperty
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyNamedChildEntity(
  entity: NamedChildEntity,
  modification: ModifiableNamedChildEntity.() -> Unit,
): NamedChildEntity = modifyEntity(ModifiableNamedChildEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createNamedChildEntity")
fun NamedChildEntity(
  childProperty: String,
  entitySource: EntitySource,
  init: (ModifiableNamedChildEntity.() -> Unit)? = null,
): ModifiableNamedChildEntity = NamedChildEntityType(childProperty, entitySource, init)
