// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ModifiableXParentEntity : ModifiableWorkspaceEntity<XParentEntity> {
  override var entitySource: EntitySource
  var parentProperty: String
  var children: List<ModifiableXChildEntity>
  var optionalChildren: List<ModifiableXChildWithOptionalParentEntity>
  var childChild: List<ModifiableXChildChildEntity>
}

internal object XParentEntityType : EntityType<XParentEntity, ModifiableXParentEntity>() {
  override val entityClass: Class<XParentEntity> get() = XParentEntity::class.java
  operator fun invoke(
    parentProperty: String,
    entitySource: EntitySource,
    init: (ModifiableXParentEntity.() -> Unit)? = null,
  ): ModifiableXParentEntity {
    val builder = builder()
    builder.parentProperty = parentProperty
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyXParentEntity(
  entity: XParentEntity,
  modification: ModifiableXParentEntity.() -> Unit,
): XParentEntity = modifyEntity(ModifiableXParentEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createXParentEntity")
fun XParentEntity(
  parentProperty: String,
  entitySource: EntitySource,
  init: (ModifiableXParentEntity.() -> Unit)? = null,
): ModifiableXParentEntity = XParentEntityType(parentProperty, entitySource, init)
