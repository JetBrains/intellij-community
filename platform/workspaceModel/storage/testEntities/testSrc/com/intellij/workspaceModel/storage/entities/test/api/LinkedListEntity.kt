package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.*
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.MutableEntityStorage




data class LinkedListEntityId(val name: String) : PersistentEntityId<LinkedListEntity> {
  override val presentableName: String
    get() = name
}

interface LinkedListEntity : WorkspaceEntityWithPersistentId {
  val myName: String
  val next: LinkedListEntityId

  override val persistentId: LinkedListEntityId
    get() = LinkedListEntityId(myName)


  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(1)
  interface Builder: LinkedListEntity, ModifiableWorkspaceEntity<LinkedListEntity>, ObjBuilder<LinkedListEntity> {
      override var myName: String
      override var entitySource: EntitySource
      override var next: LinkedListEntityId
  }
  
  companion object: Type<LinkedListEntity, Builder>() {
      operator fun invoke(myName: String, entitySource: EntitySource, next: LinkedListEntityId, init: (Builder.() -> Unit)? = null): LinkedListEntity {
          val builder = builder()
          builder.myName = myName
          builder.entitySource = entitySource
          builder.next = next
          init?.invoke(builder)
          return builder
      }
  }
  //@formatter:on
  //endregion

}
//region generated code
fun MutableEntityStorage.modifyEntity(entity: LinkedListEntity, modification: LinkedListEntity.Builder.() -> Unit) = modifyEntity(LinkedListEntity.Builder::class.java, entity, modification)
//endregion

fun MutableEntityStorage.addLinkedListEntity(name: String, next: LinkedListEntityId): LinkedListEntity {
  val linkedListEntity = LinkedListEntity(name, MySource, next)
  this.addEntity(linkedListEntity)
  return linkedListEntity
}