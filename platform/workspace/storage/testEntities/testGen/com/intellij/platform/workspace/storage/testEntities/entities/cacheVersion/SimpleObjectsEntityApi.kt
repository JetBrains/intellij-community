// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Open

@GeneratedCodeApiVersion(3)
interface ModifiableSimpleObjectsEntity : ModifiableWorkspaceEntity<SimpleObjectsEntity> {
  override var entitySource: EntitySource
  var someData: SimpleObjectsSealedClass
}

internal object SimpleObjectsEntityType : EntityType<SimpleObjectsEntity, ModifiableSimpleObjectsEntity>() {
  override val entityClass: Class<SimpleObjectsEntity> get() = SimpleObjectsEntity::class.java
  operator fun invoke(
    someData: SimpleObjectsSealedClass,
    entitySource: EntitySource,
    init: (ModifiableSimpleObjectsEntity.() -> Unit)? = null,
  ): ModifiableSimpleObjectsEntity {
    val builder = builder()
    builder.someData = someData
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifySimpleObjectsEntity(
  entity: SimpleObjectsEntity,
  modification: ModifiableSimpleObjectsEntity.() -> Unit,
): SimpleObjectsEntity = modifyEntity(ModifiableSimpleObjectsEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createSimpleObjectsEntity")
fun SimpleObjectsEntity(
  someData: SimpleObjectsSealedClass,
  entitySource: EntitySource,
  init: (ModifiableSimpleObjectsEntity.() -> Unit)? = null,
): ModifiableSimpleObjectsEntity = SimpleObjectsEntityType(someData, entitySource, init)
