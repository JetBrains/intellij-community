package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.impl.containers.toMutableWorkspaceList
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import java.util.UUID
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion

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
  @GeneratedCodeApiVersion(1)
  interface Builder : SampleEntity, WorkspaceEntity.Builder<SampleEntity>, ObjBuilder<SampleEntity> {
    override var entitySource: EntitySource
    override var booleanProperty: Boolean
    override var stringProperty: String
    override var stringListProperty: MutableList<String>
    override var stringMapProperty: Map<String, String>
    override var fileProperty: VirtualFileUrl
    override var children: List<ChildSampleEntity>
    override var nullableData: String?
    override var randomUUID: UUID?
  }

  companion object : Type<SampleEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(booleanProperty: Boolean,
                        stringProperty: String,
                        stringListProperty: List<String>,
                        stringMapProperty: Map<String, String>,
                        fileProperty: VirtualFileUrl,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): SampleEntity {
      val builder = builder()
      builder.booleanProperty = booleanProperty
      builder.stringProperty = stringProperty
      builder.stringListProperty = stringListProperty.toMutableWorkspaceList()
      builder.stringMapProperty = stringMapProperty
      builder.fileProperty = fileProperty
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: SampleEntity, modification: SampleEntity.Builder.() -> Unit) = modifyEntity(
  SampleEntity.Builder::class.java, entity, modification)
//endregion

interface ChildSampleEntity : WorkspaceEntity {
  val data: String
  val parentEntity: SampleEntity?

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : ChildSampleEntity, WorkspaceEntity.Builder<ChildSampleEntity>, ObjBuilder<ChildSampleEntity> {
    override var entitySource: EntitySource
    override var data: String
    override var parentEntity: SampleEntity?
  }

  companion object : Type<ChildSampleEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(data: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): ChildSampleEntity {
      val builder = builder()
      builder.data = data
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: ChildSampleEntity, modification: ChildSampleEntity.Builder.() -> Unit) = modifyEntity(
  ChildSampleEntity.Builder::class.java, entity, modification)
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
  @GeneratedCodeApiVersion(1)
  interface Builder : SecondSampleEntity, WorkspaceEntity.Builder<SecondSampleEntity>, ObjBuilder<SecondSampleEntity> {
    override var entitySource: EntitySource
    override var intProperty: Int
  }

  companion object : Type<SecondSampleEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(intProperty: Int, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): SecondSampleEntity {
      val builder = builder()
      builder.intProperty = intProperty
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: SecondSampleEntity, modification: SecondSampleEntity.Builder.() -> Unit) = modifyEntity(
  SecondSampleEntity.Builder::class.java, entity, modification)
//endregion

interface SourceEntity : WorkspaceEntity {
  val data: String
  val children: List<@Child ChildSourceEntity>

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : SourceEntity, WorkspaceEntity.Builder<SourceEntity>, ObjBuilder<SourceEntity> {
    override var entitySource: EntitySource
    override var data: String
    override var children: List<ChildSourceEntity>
  }

  companion object : Type<SourceEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(data: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): SourceEntity {
      val builder = builder()
      builder.data = data
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: SourceEntity, modification: SourceEntity.Builder.() -> Unit) = modifyEntity(
  SourceEntity.Builder::class.java, entity, modification)
//endregion

interface ChildSourceEntity : WorkspaceEntity {
  val data: String
  val parentEntity: SourceEntity

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : ChildSourceEntity, WorkspaceEntity.Builder<ChildSourceEntity>, ObjBuilder<ChildSourceEntity> {
    override var entitySource: EntitySource
    override var data: String
    override var parentEntity: SourceEntity
  }

  companion object : Type<ChildSourceEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(data: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): ChildSourceEntity {
      val builder = builder()
      builder.data = data
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: ChildSourceEntity, modification: ChildSourceEntity.Builder.() -> Unit) = modifyEntity(
  ChildSourceEntity.Builder::class.java, entity, modification)
//endregion

interface SymbolicIdEntity : WorkspaceEntityWithSymbolicId {
  val data: String
  override val symbolicId: LinkedListEntityId
    get() {
      return LinkedListEntityId(data)
    }

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : SymbolicIdEntity, WorkspaceEntity.Builder<SymbolicIdEntity>, ObjBuilder<SymbolicIdEntity> {
    override var entitySource: EntitySource
    override var data: String
  }

  companion object : Type<SymbolicIdEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(data: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): SymbolicIdEntity {
      val builder = builder()
      builder.data = data
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: SymbolicIdEntity, modification: SymbolicIdEntity.Builder.() -> Unit) = modifyEntity(
  SymbolicIdEntity.Builder::class.java, entity, modification)
//endregion

