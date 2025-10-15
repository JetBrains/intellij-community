// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.currentVersion

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
interface ModifiableKeyPropEntity : ModifiableWorkspaceEntity<KeyPropEntity> {
  override var entitySource: EntitySource
  var someInt: Int
  var text: String
  var url: VirtualFileUrl
}

internal object KeyPropEntityType : EntityType<KeyPropEntity, ModifiableKeyPropEntity>() {
  override val entityClass: Class<KeyPropEntity> get() = KeyPropEntity::class.java
  operator fun invoke(
    someInt: Int,
    text: String,
    url: VirtualFileUrl,
    entitySource: EntitySource,
    init: (ModifiableKeyPropEntity.() -> Unit)? = null,
  ): ModifiableKeyPropEntity {
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
  modification: ModifiableKeyPropEntity.() -> Unit,
): KeyPropEntity = modifyEntity(ModifiableKeyPropEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createKeyPropEntity")
fun KeyPropEntity(
  someInt: Int,
  text: String,
  url: VirtualFileUrl,
  entitySource: EntitySource,
  init: (ModifiableKeyPropEntity.() -> Unit)? = null,
): ModifiableKeyPropEntity = KeyPropEntityType(someInt, text, url, entitySource, init)
