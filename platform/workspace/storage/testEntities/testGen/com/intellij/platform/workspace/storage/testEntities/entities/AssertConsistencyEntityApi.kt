// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity

@GeneratedCodeApiVersion(3)
interface ModifiableAssertConsistencyEntity : ModifiableWorkspaceEntity<AssertConsistencyEntity> {
  override var entitySource: EntitySource
  var passCheck: Boolean
}

internal object AssertConsistencyEntityType : EntityType<AssertConsistencyEntity, ModifiableAssertConsistencyEntity>() {
  override val entityClass: Class<AssertConsistencyEntity> get() = AssertConsistencyEntity::class.java
  operator fun invoke(
    passCheck: Boolean,
    entitySource: EntitySource,
    init: (ModifiableAssertConsistencyEntity.() -> Unit)? = null,
  ): ModifiableAssertConsistencyEntity {
    val builder = builder()
    builder.passCheck = passCheck
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyAssertConsistencyEntity(
  entity: AssertConsistencyEntity,
  modification: ModifiableAssertConsistencyEntity.() -> Unit,
): AssertConsistencyEntity = modifyEntity(ModifiableAssertConsistencyEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createAssertConsistencyEntity")
fun AssertConsistencyEntity(
  passCheck: Boolean,
  entitySource: EntitySource,
  init: (ModifiableAssertConsistencyEntity.() -> Unit)? = null,
): ModifiableAssertConsistencyEntity = AssertConsistencyEntityType(passCheck, entitySource, init)
