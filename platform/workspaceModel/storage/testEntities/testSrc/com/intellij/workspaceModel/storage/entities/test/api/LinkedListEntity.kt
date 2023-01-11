package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.WorkspaceEntity
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion

import com.intellij.workspaceModel.storage.MutableEntityStorage




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
  @GeneratedCodeApiVersion(1)
  interface Builder : LinkedListEntity, WorkspaceEntity.Builder<LinkedListEntity>, ObjBuilder<LinkedListEntity> {
    override var entitySource: EntitySource
    override var myName: String
    override var next: LinkedListEntityId
  }

  companion object : Type<LinkedListEntity, Builder>() {
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