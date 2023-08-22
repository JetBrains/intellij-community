// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.WorkspaceEntity
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion

import com.intellij.platform.workspace.storage.MutableEntityStorage




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
  @GeneratedCodeApiVersion(2)
  interface Builder : LinkedListEntity, WorkspaceEntity.Builder<LinkedListEntity> {
    override var entitySource: EntitySource
    override var myName: String
    override var next: LinkedListEntityId
  }

  companion object : EntityType<LinkedListEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(myName: String,
                        next: LinkedListEntityId,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): LinkedListEntity {
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
fun MutableEntityStorage.modifyEntity(entity: LinkedListEntity, modification: LinkedListEntity.Builder.() -> Unit) = modifyEntity(
  LinkedListEntity.Builder::class.java, entity, modification)
//endregion

fun MutableEntityStorage.addLinkedListEntity(name: String, next: LinkedListEntityId): LinkedListEntity {
  val linkedListEntity = LinkedListEntity(name, next, MySource)
  this.addEntity(linkedListEntity)
  return linkedListEntity
}