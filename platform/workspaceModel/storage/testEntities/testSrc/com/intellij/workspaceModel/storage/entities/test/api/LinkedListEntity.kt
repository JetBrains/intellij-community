package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.*
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity



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
  @GeneratedCodeApiVersion(0)
  interface Builder: LinkedListEntity, ModifiableWorkspaceEntity<LinkedListEntity>, ObjBuilder<LinkedListEntity> {
      override var myName: String
      override var entitySource: EntitySource
      override var next: LinkedListEntityId
  }
  
  companion object: Type<LinkedListEntity, Builder>()
  //@formatter:on
  //endregion

}

fun WorkspaceEntityStorageBuilder.addLinkedListEntity(name: String, next: LinkedListEntityId): LinkedListEntity {
  val linkedListEntity = LinkedListEntity {
    this.myName = name
    this.next = next
    this.entitySource = MySource
  }
  this.addEntity(linkedListEntity)
  return linkedListEntity
}