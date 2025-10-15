// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ModifiableXChildEntity : ModifiableWorkspaceEntity<XChildEntity> {
  override var entitySource: EntitySource
  var childProperty: String
  var dataClass: DataClassX?
  var parentEntity: ModifiableXParentEntity
  var childChild: List<ModifiableXChildChildEntity>
}

internal object XChildEntityType : EntityType<XChildEntity, ModifiableXChildEntity>() {
  override val entityClass: Class<XChildEntity> get() = XChildEntity::class.java
  operator fun invoke(
    childProperty: String,
    entitySource: EntitySource,
    init: (ModifiableXChildEntity.() -> Unit)? = null,
  ): ModifiableXChildEntity {
    val builder = builder()
    builder.childProperty = childProperty
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyXChildEntity(
  entity: XChildEntity,
  modification: ModifiableXChildEntity.() -> Unit,
): XChildEntity = modifyEntity(ModifiableXChildEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createXChildEntity")
fun XChildEntity(
  childProperty: String,
  entitySource: EntitySource,
  init: (ModifiableXChildEntity.() -> Unit)? = null,
): ModifiableXChildEntity = XChildEntityType(childProperty, entitySource, init)
