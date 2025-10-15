// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ModifiableXChildChildEntity : ModifiableWorkspaceEntity<XChildChildEntity> {
  override var entitySource: EntitySource
  var parent1: ModifiableXParentEntity
  var parent2: ModifiableXChildEntity
}

internal object XChildChildEntityType : EntityType<XChildChildEntity, ModifiableXChildChildEntity>() {
  override val entityClass: Class<XChildChildEntity> get() = XChildChildEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (ModifiableXChildChildEntity.() -> Unit)? = null,
  ): ModifiableXChildChildEntity {
    val builder = builder()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyXChildChildEntity(
  entity: XChildChildEntity,
  modification: ModifiableXChildChildEntity.() -> Unit,
): XChildChildEntity = modifyEntity(ModifiableXChildChildEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createXChildChildEntity")
fun XChildChildEntity(
  entitySource: EntitySource,
  init: (ModifiableXChildChildEntity.() -> Unit)? = null,
): ModifiableXChildChildEntity = XChildChildEntityType(entitySource, init)
