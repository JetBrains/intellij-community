// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ModifiableSimpleChildAbstractEntity : ModifiableWorkspaceEntity<SimpleChildAbstractEntity>, ModifiableSimpleAbstractEntity<SimpleChildAbstractEntity> {
  override var entitySource: EntitySource
  override var parentInList: ModifiableCompositeAbstractEntity<out CompositeAbstractEntity>?
}

internal object SimpleChildAbstractEntityType : EntityType<SimpleChildAbstractEntity, ModifiableSimpleChildAbstractEntity>() {
  override val entityClass: Class<SimpleChildAbstractEntity> get() = SimpleChildAbstractEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (ModifiableSimpleChildAbstractEntity.() -> Unit)? = null,
  ): ModifiableSimpleChildAbstractEntity {
    val builder = builder()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifySimpleChildAbstractEntity(
  entity: SimpleChildAbstractEntity,
  modification: ModifiableSimpleChildAbstractEntity.() -> Unit,
): SimpleChildAbstractEntity = modifyEntity(ModifiableSimpleChildAbstractEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createSimpleChildAbstractEntity")
fun SimpleChildAbstractEntity(
  entitySource: EntitySource,
  init: (ModifiableSimpleChildAbstractEntity.() -> Unit)? = null,
): ModifiableSimpleChildAbstractEntity = SimpleChildAbstractEntityType(entitySource, init)
