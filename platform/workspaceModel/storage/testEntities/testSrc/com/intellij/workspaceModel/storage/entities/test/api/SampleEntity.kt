package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.MutableEntityStorage
import java.util.*


interface SampleEntity : WorkspaceEntity {
  val booleanProperty: Boolean
  val stringProperty: String
  val stringListProperty: List<String>
  val stringMapProperty: Map<String, String>
  val fileProperty: VirtualFileUrl
  val children: List<@Child ChildSampleEntity>
  val nullableData: String?
  val randomUUID: UUID?

  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(1)
  interface Builder: SampleEntity, ModifiableWorkspaceEntity<SampleEntity>, ObjBuilder<SampleEntity> {
      override var booleanProperty: Boolean
      override var entitySource: EntitySource
      override var stringProperty: String
      override var stringListProperty: List<String>
      override var stringMapProperty: Map<String, String>
      override var fileProperty: VirtualFileUrl
      override var children: List<ChildSampleEntity>
      override var nullableData: String?
      override var randomUUID: UUID?
  }
  
  companion object: Type<SampleEntity, Builder>() {
      operator fun invoke(booleanProperty: Boolean, entitySource: EntitySource, stringProperty: String, stringListProperty: List<String>, stringMapProperty: Map<String, String>, fileProperty: VirtualFileUrl, init: (Builder.() -> Unit)? = null): SampleEntity {
          val builder = builder()
          builder.booleanProperty = booleanProperty
          builder.entitySource = entitySource
          builder.stringProperty = stringProperty
          builder.stringListProperty = stringListProperty
          builder.stringMapProperty = stringMapProperty
          builder.fileProperty = fileProperty
          init?.invoke(builder)
          return builder
      }
  }
  //@formatter:on
  //endregion

}
//region generated code
fun MutableEntityStorage.modifyEntity(entity: SampleEntity, modification: SampleEntity.Builder.() -> Unit) = modifyEntity(SampleEntity.Builder::class.java, entity, modification)
//endregion

interface ChildSampleEntity : WorkspaceEntity {
  val data: String
  val parentEntity: SampleEntity?

  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(1)
  interface Builder: ChildSampleEntity, ModifiableWorkspaceEntity<ChildSampleEntity>, ObjBuilder<ChildSampleEntity> {
      override var data: String
      override var entitySource: EntitySource
      override var parentEntity: SampleEntity?
  }
  
  companion object: Type<ChildSampleEntity, Builder>() {
      operator fun invoke(data: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): ChildSampleEntity {
          val builder = builder()
          builder.data = data
          builder.entitySource = entitySource
          init?.invoke(builder)
          return builder
      }
  }
  //@formatter:on
  //endregion

}
//region generated code
fun MutableEntityStorage.modifyEntity(entity: ChildSampleEntity, modification: ChildSampleEntity.Builder.() -> Unit) = modifyEntity(ChildSampleEntity.Builder::class.java, entity, modification)
//endregion

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
  @GeneratedCodeApiVersion(1)
  interface Builder: SecondSampleEntity, ModifiableWorkspaceEntity<SecondSampleEntity>, ObjBuilder<SecondSampleEntity> {
      override var intProperty: Int
      override var entitySource: EntitySource
  }
  
  companion object: Type<SecondSampleEntity, Builder>() {
      operator fun invoke(intProperty: Int, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): SecondSampleEntity {
          val builder = builder()
          builder.intProperty = intProperty
          builder.entitySource = entitySource
          init?.invoke(builder)
          return builder
      }
  }
  //@formatter:on
  //endregion

}
//region generated code
fun MutableEntityStorage.modifyEntity(entity: SecondSampleEntity, modification: SecondSampleEntity.Builder.() -> Unit) = modifyEntity(SecondSampleEntity.Builder::class.java, entity, modification)
//endregion

interface SourceEntity : WorkspaceEntity {
  val data: String
  val children: List<@Child ChildSourceEntity>

  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(1)
  interface Builder: SourceEntity, ModifiableWorkspaceEntity<SourceEntity>, ObjBuilder<SourceEntity> {
      override var data: String
      override var entitySource: EntitySource
      override var children: List<ChildSourceEntity>
  }
  
  companion object: Type<SourceEntity, Builder>() {
      operator fun invoke(data: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): SourceEntity {
          val builder = builder()
          builder.data = data
          builder.entitySource = entitySource
          init?.invoke(builder)
          return builder
      }
  }
  //@formatter:on
  //endregion

}
//region generated code
fun MutableEntityStorage.modifyEntity(entity: SourceEntity, modification: SourceEntity.Builder.() -> Unit) = modifyEntity(SourceEntity.Builder::class.java, entity, modification)
//endregion

interface ChildSourceEntity : WorkspaceEntity {
  val data: String
  val parentEntity: SourceEntity

  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(1)
  interface Builder: ChildSourceEntity, ModifiableWorkspaceEntity<ChildSourceEntity>, ObjBuilder<ChildSourceEntity> {
      override var data: String
      override var entitySource: EntitySource
      override var parentEntity: SourceEntity
  }
  
  companion object: Type<ChildSourceEntity, Builder>() {
      operator fun invoke(data: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): ChildSourceEntity {
          val builder = builder()
          builder.data = data
          builder.entitySource = entitySource
          init?.invoke(builder)
          return builder
      }
  }
  //@formatter:on
  //endregion

}
//region generated code
fun MutableEntityStorage.modifyEntity(entity: ChildSourceEntity, modification: ChildSourceEntity.Builder.() -> Unit) = modifyEntity(ChildSourceEntity.Builder::class.java, entity, modification)
//endregion

interface PersistentIdEntity : WorkspaceEntityWithPersistentId {
  val data: String
  override val persistentId: LinkedListEntityId
    get() {
      return LinkedListEntityId(data)
    }

  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(1)
  interface Builder: PersistentIdEntity, ModifiableWorkspaceEntity<PersistentIdEntity>, ObjBuilder<PersistentIdEntity> {
      override var data: String
      override var entitySource: EntitySource
  }
  
  companion object: Type<PersistentIdEntity, Builder>() {
      operator fun invoke(data: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): PersistentIdEntity {
          val builder = builder()
          builder.data = data
          builder.entitySource = entitySource
          init?.invoke(builder)
          return builder
      }
  }
  //@formatter:on
  //endregion

}
//region generated code
fun MutableEntityStorage.modifyEntity(entity: PersistentIdEntity, modification: PersistentIdEntity.Builder.() -> Unit) = modifyEntity(PersistentIdEntity.Builder::class.java, entity, modification)
//endregion

