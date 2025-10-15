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
interface ModifiableSimpleSealedClassEntity : ModifiableWorkspaceEntity<SimpleSealedClassEntity> {
  override var entitySource: EntitySource
  var text: String
  var someData: SimpleSealedClass
}

internal object SimpleSealedClassEntityType : EntityType<SimpleSealedClassEntity, ModifiableSimpleSealedClassEntity>() {
  override val entityClass: Class<SimpleSealedClassEntity> get() = SimpleSealedClassEntity::class.java
  operator fun invoke(
    text: String,
    someData: SimpleSealedClass,
    entitySource: EntitySource,
    init: (ModifiableSimpleSealedClassEntity.() -> Unit)? = null,
  ): ModifiableSimpleSealedClassEntity {
    val builder = builder()
    builder.text = text
    builder.someData = someData
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifySimpleSealedClassEntity(
  entity: SimpleSealedClassEntity,
  modification: ModifiableSimpleSealedClassEntity.() -> Unit,
): SimpleSealedClassEntity = modifyEntity(ModifiableSimpleSealedClassEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createSimpleSealedClassEntity")
fun SimpleSealedClassEntity(
  text: String,
  someData: SimpleSealedClass,
  entitySource: EntitySource,
  init: (ModifiableSimpleSealedClassEntity.() -> Unit)? = null,
): ModifiableSimpleSealedClassEntity = SimpleSealedClassEntityType(text, someData, entitySource, init)
