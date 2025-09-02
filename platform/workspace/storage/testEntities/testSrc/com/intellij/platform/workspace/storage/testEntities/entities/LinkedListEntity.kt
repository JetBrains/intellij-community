// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*


data class LinkedListEntityId(val name: String) : SymbolicEntityId<LinkedListEntity> {
  override val presentableName: String
    get() = name
}

interface LinkedListEntity : WorkspaceEntityWithSymbolicId {
  val myName: String
  val next: LinkedListEntityId

  override val symbolicId: LinkedListEntityId
    get() = LinkedListEntityId(myName)

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<LinkedListEntity> {
    override var entitySource: EntitySource
    var myName: String
    var next: LinkedListEntityId
  }

  companion object : EntityType<LinkedListEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      myName: String,
      next: LinkedListEntityId,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.myName = myName
      builder.next = next
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyLinkedListEntity(
  entity: LinkedListEntity,
  modification: LinkedListEntity.Builder.() -> Unit,
): LinkedListEntity {
  return modifyEntity(LinkedListEntity.Builder::class.java, entity, modification)
}
//endregion
