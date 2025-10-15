// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ModifiableMainEntityToParent : ModifiableWorkspaceEntity<MainEntityToParent> {
  override var entitySource: EntitySource
  var x: String
  var child: ModifiableAttachedEntityToParent?
  var childNullableParent: ModifiableAttachedEntityToNullableParent?
}

internal object MainEntityToParentType : EntityType<MainEntityToParent, ModifiableMainEntityToParent>() {
  override val entityClass: Class<MainEntityToParent> get() = MainEntityToParent::class.java
  operator fun invoke(
    x: String,
    entitySource: EntitySource,
    init: (ModifiableMainEntityToParent.() -> Unit)? = null,
  ): ModifiableMainEntityToParent {
    val builder = builder()
    builder.x = x
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyMainEntityToParent(
  entity: MainEntityToParent,
  modification: ModifiableMainEntityToParent.() -> Unit,
): MainEntityToParent = modifyEntity(ModifiableMainEntityToParent::class.java, entity, modification)

@JvmOverloads
@JvmName("createMainEntityToParent")
fun MainEntityToParent(
  x: String,
  entitySource: EntitySource,
  init: (ModifiableMainEntityToParent.() -> Unit)? = null,
): ModifiableMainEntityToParent = MainEntityToParentType(x, entitySource, init)
