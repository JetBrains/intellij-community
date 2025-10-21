// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("AssertConsistencyEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage

@GeneratedCodeApiVersion(3)
interface AssertConsistencyEntityBuilder : WorkspaceEntityBuilder<AssertConsistencyEntity> {
  override var entitySource: EntitySource
  var passCheck: Boolean
}

internal object AssertConsistencyEntityType : EntityType<AssertConsistencyEntity, AssertConsistencyEntityBuilder>() {
  override val entityClass: Class<AssertConsistencyEntity> get() = AssertConsistencyEntity::class.java
  operator fun invoke(
    passCheck: Boolean,
    entitySource: EntitySource,
    init: (AssertConsistencyEntityBuilder.() -> Unit)? = null,
  ): AssertConsistencyEntityBuilder {
    val builder = builder()
    builder.passCheck = passCheck
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyAssertConsistencyEntity(
  entity: AssertConsistencyEntity,
  modification: AssertConsistencyEntityBuilder.() -> Unit,
): AssertConsistencyEntity = modifyEntity(AssertConsistencyEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createAssertConsistencyEntity")
fun AssertConsistencyEntity(
  passCheck: Boolean,
  entitySource: EntitySource,
  init: (AssertConsistencyEntityBuilder.() -> Unit)? = null,
): AssertConsistencyEntityBuilder = AssertConsistencyEntityType(passCheck, entitySource, init)
