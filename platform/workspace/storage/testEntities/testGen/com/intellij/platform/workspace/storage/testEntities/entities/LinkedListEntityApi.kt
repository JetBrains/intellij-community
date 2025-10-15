// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*

@GeneratedCodeApiVersion(3)
interface ModifiableLinkedListEntity : ModifiableWorkspaceEntity<LinkedListEntity> {
  override var entitySource: EntitySource
  var myName: String
  var next: LinkedListEntityId
}

internal object LinkedListEntityType : EntityType<LinkedListEntity, ModifiableLinkedListEntity>() {
  override val entityClass: Class<LinkedListEntity> get() = LinkedListEntity::class.java
  operator fun invoke(
    myName: String,
    next: LinkedListEntityId,
    entitySource: EntitySource,
    init: (ModifiableLinkedListEntity.() -> Unit)? = null,
  ): ModifiableLinkedListEntity {
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
  modification: ModifiableLinkedListEntity.() -> Unit,
): LinkedListEntity = modifyEntity(ModifiableLinkedListEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createLinkedListEntity")
fun LinkedListEntity(
  myName: String,
  next: LinkedListEntityId,
  entitySource: EntitySource,
  init: (ModifiableLinkedListEntity.() -> Unit)? = null,
): ModifiableLinkedListEntity = LinkedListEntityType(myName, next, entitySource, init)
