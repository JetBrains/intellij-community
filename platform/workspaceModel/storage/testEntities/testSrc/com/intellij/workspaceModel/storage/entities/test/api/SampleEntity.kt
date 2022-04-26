package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity



interface SampleEntity : WorkspaceEntity {
  val booleanProperty: Boolean
  val stringProperty: String
  val stringListProperty: List<String>
  val fileProperty: VirtualFileUrl
  val children: List<@Child ChildSampleEntity>
  val nullableData: String?

  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: SampleEntity, ModifiableWorkspaceEntity<SampleEntity>, ObjBuilder<SampleEntity> {
      override var booleanProperty: Boolean
      override var entitySource: EntitySource
      override var stringProperty: String
      override var stringListProperty: List<String>
      override var fileProperty: VirtualFileUrl
      override var children: List<ChildSampleEntity>
      override var nullableData: String?
  }
  
  companion object: Type<SampleEntity, Builder>() {
      operator fun invoke(booleanProperty: Boolean, entitySource: EntitySource, stringProperty: String, stringListProperty: List<String>, fileProperty: VirtualFileUrl, init: Builder.() -> Unit): SampleEntity {
          val builder = builder(init)
          builder.booleanProperty = booleanProperty
          builder.entitySource = entitySource
          builder.stringProperty = stringProperty
          builder.stringListProperty = stringListProperty
          builder.fileProperty = fileProperty
          return builder
      }
  }
  //@formatter:on
  //endregion

}

interface ChildSampleEntity : WorkspaceEntity {
  val data: String
  val parentEntity: SampleEntity?

  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: ChildSampleEntity, ModifiableWorkspaceEntity<ChildSampleEntity>, ObjBuilder<ChildSampleEntity> {
      override var data: String
      override var entitySource: EntitySource
      override var parentEntity: SampleEntity?
  }
  
  companion object: Type<ChildSampleEntity, Builder>() {
      operator fun invoke(data: String, entitySource: EntitySource, init: Builder.() -> Unit): ChildSampleEntity {
          val builder = builder(init)
          builder.data = data
          builder.entitySource = entitySource
          return builder
      }
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
  @GeneratedCodeApiVersion(0)
  interface Builder: SecondSampleEntity, ModifiableWorkspaceEntity<SecondSampleEntity>, ObjBuilder<SecondSampleEntity> {
      override var intProperty: Int
      override var entitySource: EntitySource
  }
  
  companion object: Type<SecondSampleEntity, Builder>() {
      operator fun invoke(intProperty: Int, entitySource: EntitySource, init: Builder.() -> Unit): SecondSampleEntity {
          val builder = builder(init)
          builder.intProperty = intProperty
          builder.entitySource = entitySource
          return builder
      }
  }
  //@formatter:on
  //endregion

}

interface SourceEntity : WorkspaceEntity {
  val data: String
  val children: List<@Child ChildSourceEntity>

  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: SourceEntity, ModifiableWorkspaceEntity<SourceEntity>, ObjBuilder<SourceEntity> {
      override var data: String
      override var entitySource: EntitySource
      override var children: List<ChildSourceEntity>
  }
  
  companion object: Type<SourceEntity, Builder>() {
      operator fun invoke(data: String, entitySource: EntitySource, init: Builder.() -> Unit): SourceEntity {
          val builder = builder(init)
          builder.data = data
          builder.entitySource = entitySource
          return builder
      }
  }
  //@formatter:on
  //endregion

}

interface ChildSourceEntity : WorkspaceEntity {
  val data: String
  val parentEntity: SourceEntity

  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: ChildSourceEntity, ModifiableWorkspaceEntity<ChildSourceEntity>, ObjBuilder<ChildSourceEntity> {
      override var data: String
      override var entitySource: EntitySource
      override var parentEntity: SourceEntity
  }
  
  companion object: Type<ChildSourceEntity, Builder>() {
      operator fun invoke(data: String, entitySource: EntitySource, init: Builder.() -> Unit): ChildSourceEntity {
          val builder = builder(init)
          builder.data = data
          builder.entitySource = entitySource
          return builder
      }
  }
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
  @GeneratedCodeApiVersion(0)
  interface Builder: PersistentIdEntity, ModifiableWorkspaceEntity<PersistentIdEntity>, ObjBuilder<PersistentIdEntity> {
      override var data: String
      override var entitySource: EntitySource
  }
  
  companion object: Type<PersistentIdEntity, Builder>() {
      operator fun invoke(data: String, entitySource: EntitySource, init: Builder.() -> Unit): PersistentIdEntity {
          val builder = builder(init)
          builder.data = data
          builder.entitySource = entitySource
          return builder
      }
  }
  //@formatter:on
  //endregion

}

