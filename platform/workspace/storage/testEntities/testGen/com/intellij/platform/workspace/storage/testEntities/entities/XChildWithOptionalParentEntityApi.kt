// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ModifiableXChildWithOptionalParentEntity : ModifiableWorkspaceEntity<XChildWithOptionalParentEntity> {
  override var entitySource: EntitySource
  var childProperty: String
  var optionalParent: ModifiableXParentEntity?
}

internal object XChildWithOptionalParentEntityType : EntityType<XChildWithOptionalParentEntity, ModifiableXChildWithOptionalParentEntity>() {
  override val entityClass: Class<XChildWithOptionalParentEntity> get() = XChildWithOptionalParentEntity::class.java
  operator fun invoke(
    childProperty: String,
    entitySource: EntitySource,
    init: (ModifiableXChildWithOptionalParentEntity.() -> Unit)? = null,
  ): ModifiableXChildWithOptionalParentEntity {
    val builder = builder()
    builder.childProperty = childProperty
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyXChildWithOptionalParentEntity(
  entity: XChildWithOptionalParentEntity,
  modification: ModifiableXChildWithOptionalParentEntity.() -> Unit,
): XChildWithOptionalParentEntity = modifyEntity(ModifiableXChildWithOptionalParentEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createXChildWithOptionalParentEntity")
fun XChildWithOptionalParentEntity(
  childProperty: String,
  entitySource: EntitySource,
  init: (ModifiableXChildWithOptionalParentEntity.() -> Unit)? = null,
): ModifiableXChildWithOptionalParentEntity = XChildWithOptionalParentEntityType(childProperty, entitySource, init)
