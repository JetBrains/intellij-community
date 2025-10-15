// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ModifiableOneToOneRefEntity : ModifiableWorkspaceEntity<OneToOneRefEntity> {
  override var entitySource: EntitySource
  var version: Int
  var text: String
  var anotherEntity: ModifiableAnotherOneToOneRefEntity?
}

internal object OneToOneRefEntityType : EntityType<OneToOneRefEntity, ModifiableOneToOneRefEntity>() {
  override val entityClass: Class<OneToOneRefEntity> get() = OneToOneRefEntity::class.java
  operator fun invoke(
    version: Int,
    text: String,
    entitySource: EntitySource,
    init: (ModifiableOneToOneRefEntity.() -> Unit)? = null,
  ): ModifiableOneToOneRefEntity {
    val builder = builder()
    builder.version = version
    builder.text = text
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyOneToOneRefEntity(
  entity: OneToOneRefEntity,
  modification: ModifiableOneToOneRefEntity.() -> Unit,
): OneToOneRefEntity = modifyEntity(ModifiableOneToOneRefEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createOneToOneRefEntity")
fun OneToOneRefEntity(
  version: Int,
  text: String,
  entitySource: EntitySource,
  init: (ModifiableOneToOneRefEntity.() -> Unit)? = null,
): ModifiableOneToOneRefEntity = OneToOneRefEntityType(version, text, entitySource, init)
