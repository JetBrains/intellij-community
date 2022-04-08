package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.PersistentEntityId
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.WorkspaceEntityWithPersistentId
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.impl.ObjType
import org.jetbrains.deft.impl.TBlob
import org.jetbrains.deft.impl.TString
import org.jetbrains.deft.impl.fields.Field
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import org.jetbrains.deft.Obj
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.*
import org.jetbrains.deft.TestEntities.TestEntities


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
  interface Builder: LinkedListEntity, ModifiableWorkspaceEntity<LinkedListEntity>, ObjBuilder<LinkedListEntity> {
      override var myName: String
      override var entitySource: EntitySource
      override var next: LinkedListEntityId
  }
  
  companion object: ObjType<LinkedListEntity, Builder>(TestEntities, 24)
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