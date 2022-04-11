package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.annotations.Child
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field
import com.intellij.workspaceModel.storage.EntitySource
import org.jetbrains.deft.Obj
import org.jetbrains.deft.impl.fields.*
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity

import org.jetbrains.deft.Type




interface SampleEntity : WorkspaceEntity {
  val booleanProperty: Boolean
  val stringProperty: String
  val stringListProperty: List<String>
  val fileProperty: VirtualFileUrl
  val children: List<@Child ChildSampleEntity>

  //region generated code
  //@formatter:off
  interface Builder: SampleEntity, ModifiableWorkspaceEntity<SampleEntity>, ObjBuilder<SampleEntity> {
      override var booleanProperty: Boolean
      override var entitySource: EntitySource
      override var stringProperty: String
      override var stringListProperty: List<String>
      override var fileProperty: VirtualFileUrl
      override var children: List<ChildSampleEntity>
  }
  
  companion object: Type<SampleEntity, Builder>(83)
  //@formatter:on
  //endregion

}

interface ChildSampleEntity : WorkspaceEntity {
  val data: String
  val parentEntity: SampleEntity?
  //region generated code
  //@formatter:off
  interface Builder: ChildSampleEntity, ModifiableWorkspaceEntity<ChildSampleEntity>, ObjBuilder<ChildSampleEntity> {
      override var data: String
      override var entitySource: EntitySource
      override var parentEntity: SampleEntity?
  }
  
  companion object: Type<ChildSampleEntity, Builder>(84)
  //@formatter:on
  //endregion

}

abstract class MyData(val myData: MyContainer)

class MyConcreteImpl(myData: MyContainer) : MyData(myData) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is MyConcreteImpl) return false
    return this.myData == other.myData
  }

  override fun hashCode(): Int {
    return this.myData.hashCode()
  }
}

data class MyContainer(val info: String)

interface SecondSampleEntity : WorkspaceEntity {
  val intProperty: Int

  //region generated code
  //@formatter:off
  interface Builder: SecondSampleEntity, ModifiableWorkspaceEntity<SecondSampleEntity>, ObjBuilder<SecondSampleEntity> {
      override var intProperty: Int
      override var entitySource: EntitySource
  }
  
  companion object: Type<SecondSampleEntity, Builder>(85)
  //@formatter:on
  //endregion

}

interface SourceEntity : WorkspaceEntity {
  val data: String
  val children: List<@Child ChildSourceEntity>
  //region generated code
  //@formatter:off
  interface Builder: SourceEntity, ModifiableWorkspaceEntity<SourceEntity>, ObjBuilder<SourceEntity> {
      override var data: String
      override var entitySource: EntitySource
      override var children: List<ChildSourceEntity>
  }
  
  companion object: Type<SourceEntity, Builder>(86)
  //@formatter:on
  //endregion

}

interface ChildSourceEntity : WorkspaceEntity {
  val data: String
  val parentEntity: SourceEntity
  //region generated code
  //@formatter:off
  interface Builder: ChildSourceEntity, ModifiableWorkspaceEntity<ChildSourceEntity>, ObjBuilder<ChildSourceEntity> {
      override var data: String
      override var entitySource: EntitySource
      override var parentEntity: SourceEntity
  }
  
  companion object: Type<ChildSourceEntity, Builder>(87)
  //@formatter:on
  //endregion

}

interface PersistentIdEntity : WorkspaceEntityWithPersistentId {
  val data: String
  override val persistentId: LinkedListEntityId
    get() {
      return LinkedListEntityId(data)
    }
  //region generated code
  //@formatter:off
  interface Builder: PersistentIdEntity, ModifiableWorkspaceEntity<PersistentIdEntity>, ObjBuilder<PersistentIdEntity> {
      override var data: String
      override var entitySource: EntitySource
  }
  
  companion object: Type<PersistentIdEntity, Builder>(88)
  //@formatter:on
  //endregion

}

