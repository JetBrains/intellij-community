// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import java.util.UUID
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion

import com.intellij.platform.workspace.storage.MutableEntityStorage


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
  @GeneratedCodeApiVersion(2)
  interface Builder : SampleEntity, WorkspaceEntity.Builder<SampleEntity> {
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

  companion object : EntityType<SampleEntity, Builder>() {
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
  @GeneratedCodeApiVersion(2)
  interface Builder : ChildSampleEntity, WorkspaceEntity.Builder<ChildSampleEntity> {
    override var entitySource: EntitySource
    override var data: String
    override var parentEntity: SampleEntity?
  }

  companion object : EntityType<ChildSampleEntity, Builder>() {
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
  @GeneratedCodeApiVersion(2)
  interface Builder : SecondSampleEntity, WorkspaceEntity.Builder<SecondSampleEntity> {
    override var entitySource: EntitySource
    override var intProperty: Int
  }

  companion object : EntityType<SecondSampleEntity, Builder>() {
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
  @GeneratedCodeApiVersion(2)
  interface Builder : SourceEntity, WorkspaceEntity.Builder<SourceEntity> {
    override var entitySource: EntitySource
    override var data: String
    override var children: List<ChildSourceEntity>
  }

  companion object : EntityType<SourceEntity, Builder>() {
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
  @GeneratedCodeApiVersion(2)
  interface Builder : ChildSourceEntity, WorkspaceEntity.Builder<ChildSourceEntity> {
    override var entitySource: EntitySource
    override var data: String
    override var parentEntity: SourceEntity
  }

  companion object : EntityType<ChildSourceEntity, Builder>() {
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
  @GeneratedCodeApiVersion(2)
  interface Builder : SymbolicIdEntity, WorkspaceEntity.Builder<SymbolicIdEntity> {
    override var entitySource: EntitySource
    override var data: String
  }

  companion object : EntityType<SymbolicIdEntity, Builder>() {
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

