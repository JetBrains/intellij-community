// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Open
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList

@GeneratedCodeApiVersion(3)
interface ModifiableOneEntityWithSymbolicId : ModifiableWorkspaceEntity<OneEntityWithSymbolicId> {
  override var entitySource: EntitySource
  var myName: String
}

internal object OneEntityWithSymbolicIdType : EntityType<OneEntityWithSymbolicId, ModifiableOneEntityWithSymbolicId>() {
  override val entityClass: Class<OneEntityWithSymbolicId> get() = OneEntityWithSymbolicId::class.java
  operator fun invoke(
    myName: String,
    entitySource: EntitySource,
    init: (ModifiableOneEntityWithSymbolicId.() -> Unit)? = null,
  ): ModifiableOneEntityWithSymbolicId {
    val builder = builder()
    builder.myName = myName
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyOneEntityWithSymbolicId(
  entity: OneEntityWithSymbolicId,
  modification: ModifiableOneEntityWithSymbolicId.() -> Unit,
): OneEntityWithSymbolicId = modifyEntity(ModifiableOneEntityWithSymbolicId::class.java, entity, modification)

@JvmOverloads
@JvmName("createOneEntityWithSymbolicId")
fun OneEntityWithSymbolicId(
  myName: String,
  entitySource: EntitySource,
  init: (ModifiableOneEntityWithSymbolicId.() -> Unit)? = null,
): ModifiableOneEntityWithSymbolicId = OneEntityWithSymbolicIdType(myName, entitySource, init)
