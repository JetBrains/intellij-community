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
interface ModifiableAnotherOneToManyRefEntity : ModifiableWorkspaceEntity<AnotherOneToManyRefEntity> {
  override var entitySource: EntitySource
  var parentEntity: ModifiableOneToManyRefEntity
  var version: Int
  var someData: OneToManyRefDataClass
}

internal object AnotherOneToManyRefEntityType : EntityType<AnotherOneToManyRefEntity, ModifiableAnotherOneToManyRefEntity>() {
  override val entityClass: Class<AnotherOneToManyRefEntity> get() = AnotherOneToManyRefEntity::class.java
  operator fun invoke(
    version: Int,
    someData: OneToManyRefDataClass,
    entitySource: EntitySource,
    init: (ModifiableAnotherOneToManyRefEntity.() -> Unit)? = null,
  ): ModifiableAnotherOneToManyRefEntity {
    val builder = builder()
    builder.version = version
    builder.someData = someData
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyAnotherOneToManyRefEntity(
  entity: AnotherOneToManyRefEntity,
  modification: ModifiableAnotherOneToManyRefEntity.() -> Unit,
): AnotherOneToManyRefEntity = modifyEntity(ModifiableAnotherOneToManyRefEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createAnotherOneToManyRefEntity")
fun AnotherOneToManyRefEntity(
  version: Int,
  someData: OneToManyRefDataClass,
  entitySource: EntitySource,
  init: (ModifiableAnotherOneToManyRefEntity.() -> Unit)? = null,
): ModifiableAnotherOneToManyRefEntity = AnotherOneToManyRefEntityType(version, someData, entitySource, init)
