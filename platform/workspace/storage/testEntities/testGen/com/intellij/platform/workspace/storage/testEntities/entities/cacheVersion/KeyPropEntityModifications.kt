// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("KeyPropEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
interface KeyPropEntityBuilder : WorkspaceEntityBuilder<KeyPropEntity> {
  override var entitySource: EntitySource
  var someInt: Int
  var text: String
  var url: VirtualFileUrl
}

internal object KeyPropEntityType : EntityType<KeyPropEntity, KeyPropEntityBuilder>() {
  override val entityClass: Class<KeyPropEntity> get() = KeyPropEntity::class.java
  operator fun invoke(
    someInt: Int,
    text: String,
    url: VirtualFileUrl,
    entitySource: EntitySource,
    init: (KeyPropEntityBuilder.() -> Unit)? = null,
  ): KeyPropEntityBuilder {
    val builder = builder()
    builder.someInt = someInt
    builder.text = text
    builder.url = url
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyKeyPropEntity(
  entity: KeyPropEntity,
  modification: KeyPropEntityBuilder.() -> Unit,
): KeyPropEntity = modifyEntity(KeyPropEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createKeyPropEntity")
fun KeyPropEntity(
  someInt: Int,
  text: String,
  url: VirtualFileUrl,
  entitySource: EntitySource,
  init: (KeyPropEntityBuilder.() -> Unit)? = null,
): KeyPropEntityBuilder = KeyPropEntityType(someInt, text, url, entitySource, init)
