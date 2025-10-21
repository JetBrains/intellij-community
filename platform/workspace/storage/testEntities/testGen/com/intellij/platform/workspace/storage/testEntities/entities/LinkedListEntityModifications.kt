// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("LinkedListEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*

@GeneratedCodeApiVersion(3)
interface LinkedListEntityBuilder : WorkspaceEntityBuilder<LinkedListEntity> {
  override var entitySource: EntitySource
  var myName: String
  var next: LinkedListEntityId
}

internal object LinkedListEntityType : EntityType<LinkedListEntity, LinkedListEntityBuilder>() {
  override val entityClass: Class<LinkedListEntity> get() = LinkedListEntity::class.java
  operator fun invoke(
    myName: String,
    next: LinkedListEntityId,
    entitySource: EntitySource,
    init: (LinkedListEntityBuilder.() -> Unit)? = null,
  ): LinkedListEntityBuilder {
    val builder = builder()
    builder.myName = myName
    builder.next = next
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyLinkedListEntity(
  entity: LinkedListEntity,
  modification: LinkedListEntityBuilder.() -> Unit,
): LinkedListEntity = modifyEntity(LinkedListEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createLinkedListEntity")
fun LinkedListEntity(
  myName: String,
  next: LinkedListEntityId,
  entitySource: EntitySource,
  init: (LinkedListEntityBuilder.() -> Unit)? = null,
): LinkedListEntityBuilder = LinkedListEntityType(myName, next, entitySource, init)
