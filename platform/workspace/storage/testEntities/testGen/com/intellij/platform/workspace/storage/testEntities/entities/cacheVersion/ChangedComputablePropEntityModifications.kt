// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ChangedComputablePropEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion

import com.intellij.platform.workspace.storage.*

@GeneratedCodeApiVersion(3)
interface ChangedComputablePropEntityBuilder : WorkspaceEntityBuilder<ChangedComputablePropEntity> {
  override var entitySource: EntitySource
  var text: String
}

internal object ChangedComputablePropEntityType : EntityType<ChangedComputablePropEntity, ChangedComputablePropEntityBuilder>() {
  override val entityClass: Class<ChangedComputablePropEntity> get() = ChangedComputablePropEntity::class.java
  operator fun invoke(
    text: String,
    entitySource: EntitySource,
    init: (ChangedComputablePropEntityBuilder.() -> Unit)? = null,
  ): ChangedComputablePropEntityBuilder {
    val builder = builder()
    builder.text = text
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyChangedComputablePropEntity(
  entity: ChangedComputablePropEntity,
  modification: ChangedComputablePropEntityBuilder.() -> Unit,
): ChangedComputablePropEntity = modifyEntity(ChangedComputablePropEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createChangedComputablePropEntity")
fun ChangedComputablePropEntity(
  text: String,
  entitySource: EntitySource,
  init: (ChangedComputablePropEntityBuilder.() -> Unit)? = null,
): ChangedComputablePropEntityBuilder = ChangedComputablePropEntityType(text, entitySource, init)
