// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ModifiableKeyChild : ModifiableWorkspaceEntity<KeyChild> {
  override var entitySource: EntitySource
  var data: String
  var parentEntity: ModifiableKeyParent
}

internal object KeyChildType : EntityType<KeyChild, ModifiableKeyChild>() {
  override val entityClass: Class<KeyChild> get() = KeyChild::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (ModifiableKeyChild.() -> Unit)? = null,
  ): ModifiableKeyChild {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyKeyChild(
  entity: KeyChild,
  modification: ModifiableKeyChild.() -> Unit,
): KeyChild = modifyEntity(ModifiableKeyChild::class.java, entity, modification)

@JvmOverloads
@JvmName("createKeyChild")
fun KeyChild(
  data: String,
  entitySource: EntitySource,
  init: (ModifiableKeyChild.() -> Unit)? = null,
): ModifiableKeyChild = KeyChildType(data, entitySource, init)
