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
interface ModifiableOneToManyRefEntity : ModifiableWorkspaceEntity<OneToManyRefEntity> {
  override var entitySource: EntitySource
  var someData: OneToManyRefDataClass
  var anotherEntity: List<ModifiableAnotherOneToManyRefEntity>
}

internal object OneToManyRefEntityType : EntityType<OneToManyRefEntity, ModifiableOneToManyRefEntity>() {
  override val entityClass: Class<OneToManyRefEntity> get() = OneToManyRefEntity::class.java
  operator fun invoke(
    someData: OneToManyRefDataClass,
    entitySource: EntitySource,
    init: (ModifiableOneToManyRefEntity.() -> Unit)? = null,
  ): ModifiableOneToManyRefEntity {
    val builder = builder()
    builder.someData = someData
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyOneToManyRefEntity(
  entity: OneToManyRefEntity,
  modification: ModifiableOneToManyRefEntity.() -> Unit,
): OneToManyRefEntity = modifyEntity(ModifiableOneToManyRefEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createOneToManyRefEntity")
fun OneToManyRefEntity(
  someData: OneToManyRefDataClass,
  entitySource: EntitySource,
  init: (ModifiableOneToManyRefEntity.() -> Unit)? = null,
): ModifiableOneToManyRefEntity = OneToManyRefEntityType(someData, entitySource, init)
