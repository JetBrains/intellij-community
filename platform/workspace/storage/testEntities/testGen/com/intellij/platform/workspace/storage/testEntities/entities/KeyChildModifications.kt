// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("KeyChildModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*

@GeneratedCodeApiVersion(3)
interface KeyChildBuilder : WorkspaceEntityBuilder<KeyChild> {
  override var entitySource: EntitySource
  var data: String
  var parentEntity: KeyParentBuilder
}

internal object KeyChildType : EntityType<KeyChild, KeyChildBuilder>() {
  override val entityClass: Class<KeyChild> get() = KeyChild::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (KeyChildBuilder.() -> Unit)? = null,
  ): KeyChildBuilder {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyKeyChild(
  entity: KeyChild,
  modification: KeyChildBuilder.() -> Unit,
): KeyChild = modifyEntity(KeyChildBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createKeyChild")
fun KeyChild(
  data: String,
  entitySource: EntitySource,
  init: (KeyChildBuilder.() -> Unit)? = null,
): KeyChildBuilder = KeyChildType(data, entitySource, init)
