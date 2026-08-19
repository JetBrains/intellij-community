// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ChangedComputableSymbolicIdEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.impl.ChangedComputableSymbolicIdEntityImpl

@GeneratedCodeApiVersion(3)
interface ChangedComputableSymbolicIdEntityBuilder : WorkspaceEntityBuilder<ChangedComputableSymbolicIdEntity> {
  override var entitySource: EntitySource
  var text: String
}

internal object ChangedComputableSymbolicIdEntityType :
  EntityType<ChangedComputableSymbolicIdEntity, ChangedComputableSymbolicIdEntityBuilder>() {
  override val entityImplClass: Class<*> get() = ChangedComputableSymbolicIdEntityImpl::class.java
  override val entityImplBuilderClass: Class<*> get() = ChangedComputableSymbolicIdEntityImpl.Builder::class.java
  operator fun invoke(
    text: String,
    entitySource: EntitySource,
    init: (ChangedComputableSymbolicIdEntityBuilder.() -> Unit)? = null,
  ): ChangedComputableSymbolicIdEntityBuilder {
    val builder = builder()
    builder.text = text
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyChangedComputableSymbolicIdEntity(
  entity: ChangedComputableSymbolicIdEntity,
  modification: ChangedComputableSymbolicIdEntityBuilder.() -> Unit,
): ChangedComputableSymbolicIdEntity = modifyEntity(ChangedComputableSymbolicIdEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createChangedComputableSymbolicIdEntity")
fun ChangedComputableSymbolicIdEntity(
  text: String,
  entitySource: EntitySource,
  init: (ChangedComputableSymbolicIdEntityBuilder.() -> Unit)? = null,
): ChangedComputableSymbolicIdEntityBuilder = ChangedComputableSymbolicIdEntityType(text, entitySource, init)
