package com.intellij.workspaceModel.storage.entities.api

import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import org.jetbrains.deft.IntellijWs.IntellijWs
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.annotations.Child
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field
import com.intellij.workspaceModel.storage.EntitySource
import org.jetbrains.deft.Obj
import org.jetbrains.deft.impl.fields.*
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity














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
  
  companion object: ObjType<SampleEntity, Builder>(IntellijWs, 27) {
      val booleanProperty: Field<SampleEntity, Boolean> = Field(this, 0, "booleanProperty", TBoolean)
      val entitySource: Field<SampleEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
      val stringProperty: Field<SampleEntity, String> = Field(this, 0, "stringProperty", TString)
      val stringListProperty: Field<SampleEntity, List<String>> = Field(this, 0, "stringListProperty", TList(TString))
      val fileProperty: Field<SampleEntity, VirtualFileUrl> = Field(this, 0, "fileProperty", TBlob("VirtualFileUrl"))
      val children: Field<SampleEntity, List<ChildSampleEntity>> = Field(this, 0, "children", TList(TRef("org.jetbrains.deft.IntellijWs", 28, child = true)))
  }
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
  
  companion object: ObjType<ChildSampleEntity, Builder>(IntellijWs, 28) {
      val data: Field<ChildSampleEntity, String> = Field(this, 0, "data", TString)
      val entitySource: Field<ChildSampleEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
      val parentEntity: Field<ChildSampleEntity, SampleEntity?> = Field(this, 0, "parentEntity", TOptional(TRef("org.jetbrains.deft.IntellijWs", 27)))
  }
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
  
  companion object: ObjType<SecondSampleEntity, Builder>(IntellijWs, 29) {
      val intProperty: Field<SecondSampleEntity, Int> = Field(this, 0, "intProperty", TInt)
      val entitySource: Field<SecondSampleEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
  }
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
  
  companion object: ObjType<SourceEntity, Builder>(IntellijWs, 30) {
      val data: Field<SourceEntity, String> = Field(this, 0, "data", TString)
      val entitySource: Field<SourceEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
      val children: Field<SourceEntity, List<ChildSourceEntity>> = Field(this, 0, "children", TList(TRef("org.jetbrains.deft.IntellijWs", 31, child = true)))
  }
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
  
  companion object: ObjType<ChildSourceEntity, Builder>(IntellijWs, 31) {
      val data: Field<ChildSourceEntity, String> = Field(this, 0, "data", TString)
      val entitySource: Field<ChildSourceEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
      val parentEntity: Field<ChildSourceEntity, SourceEntity> = Field(this, 0, "parentEntity", TRef("org.jetbrains.deft.IntellijWs", 30))
  }
  //@formatter:on
  //endregion

}

data class LinkedListEntityId(val name: String) : PersistentEntityId<LinkedListEntity> {
  override val presentableName: String
    get() = name
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
  
  companion object: ObjType<PersistentIdEntity, Builder>(IntellijWs, 32) {
      val data: Field<PersistentIdEntity, String> = Field(this, 0, "data", TString)
      val entitySource: Field<PersistentIdEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
      val persistentId: Field<PersistentIdEntity, LinkedListEntityId> = Field(this, 0, "persistentId", TBlob("LinkedListEntityId"))
  }
  //@formatter:on
  //endregion

}

