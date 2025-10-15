// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ModifiableKeyParent : ModifiableWorkspaceEntity<KeyParent> {
  override var entitySource: EntitySource
  var keyField: String
  var notKeyField: String
  var children: List<ModifiableKeyChild>
}

internal object KeyParentType : EntityType<KeyParent, ModifiableKeyParent>() {
  override val entityClass: Class<KeyParent> get() = KeyParent::class.java
  operator fun invoke(
    keyField: String,
    notKeyField: String,
    entitySource: EntitySource,
    init: (ModifiableKeyParent.() -> Unit)? = null,
  ): ModifiableKeyParent {
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
  modification: ModifiableKeyParent.() -> Unit,
): KeyParent = modifyEntity(ModifiableKeyParent::class.java, entity, modification)

@JvmOverloads
@JvmName("createKeyParent")
fun KeyParent(
  keyField: String,
  notKeyField: String,
  entitySource: EntitySource,
  init: (ModifiableKeyParent.() -> Unit)? = null,
): ModifiableKeyParent = KeyParentType(keyField, notKeyField, entitySource, init)
