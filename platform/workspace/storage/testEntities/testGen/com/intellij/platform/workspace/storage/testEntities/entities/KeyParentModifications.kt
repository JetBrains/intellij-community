// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("KeyParentModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.impl.KeyParentImpl

@GeneratedCodeApiVersion(3)
interface KeyParentBuilder : WorkspaceEntityBuilder<KeyParent> {
  override var entitySource: EntitySource
  var keyField: String
  var notKeyField: String
  var children: List<KeyChildBuilder>
}

internal object KeyParentType : EntityType<KeyParent, KeyParentBuilder>() {
  override val entityClass: Class<KeyParent> get() = KeyParent::class.java
  override val entityImplBuilderClass: Class<*> get() = KeyParentImpl.Builder::class.java
  operator fun invoke(
    keyField: String,
    notKeyField: String,
    entitySource: EntitySource,
    init: (KeyParentBuilder.() -> Unit)? = null,
  ): KeyParentBuilder {
    val builder = builder()
    builder.keyField = keyField
    builder.notKeyField = notKeyField
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyKeyParent(
  entity: KeyParent,
  modification: KeyParentBuilder.() -> Unit,
): KeyParent = modifyEntity(KeyParentBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createKeyParent")
fun KeyParent(
  keyField: String,
  notKeyField: String,
  entitySource: EntitySource,
  init: (KeyParentBuilder.() -> Unit)? = null,
): KeyParentBuilder = KeyParentType(keyField, notKeyField, entitySource, init)
