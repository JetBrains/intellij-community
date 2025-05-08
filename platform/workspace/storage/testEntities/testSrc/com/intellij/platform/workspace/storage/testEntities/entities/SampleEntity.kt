// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import java.util.UUID


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
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<SampleEntity> {
    override var entitySource: EntitySource
    var booleanProperty: Boolean
    var stringProperty: String
    var stringListProperty: MutableList<String>
    var stringMapProperty: Map<String, String>
    var fileProperty: VirtualFileUrl
    var children: List<ChildSampleEntity.Builder>
    var nullableData: String?
    var randomUUID: UUID?
  }

  companion object : EntityType<SampleEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      booleanProperty: Boolean,
      stringProperty: String,
      stringListProperty: List<String>,
      stringMapProperty: Map<String, String>,
      fileProperty: VirtualFileUrl,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
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
fun MutableEntityStorage.modifySampleEntity(
  entity: SampleEntity,
  modification: SampleEntity.Builder.() -> Unit,
): SampleEntity {
  return modifyEntity(SampleEntity.Builder::class.java, entity, modification)
}
//endregion

interface ChildSampleEntity : WorkspaceEntity {
  val data: String
  val parentEntity: SampleEntity?

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ChildSampleEntity> {
    override var entitySource: EntitySource
    var data: String
    var parentEntity: SampleEntity.Builder?
  }

  companion object : EntityType<ChildSampleEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      data: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
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
fun MutableEntityStorage.modifyChildSampleEntity(
  entity: ChildSampleEntity,
  modification: ChildSampleEntity.Builder.() -> Unit,
): ChildSampleEntity {
  return modifyEntity(ChildSampleEntity.Builder::class.java, entity, modification)
}
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
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<SecondSampleEntity> {
    override var entitySource: EntitySource
    var intProperty: Int
  }

  companion object : EntityType<SecondSampleEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      intProperty: Int,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
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
fun MutableEntityStorage.modifySecondSampleEntity(
  entity: SecondSampleEntity,
  modification: SecondSampleEntity.Builder.() -> Unit,
): SecondSampleEntity {
  return modifyEntity(SecondSampleEntity.Builder::class.java, entity, modification)
}
//endregion

interface SourceEntity : WorkspaceEntity {
  val data: String
  val children: List<@Child ChildSourceEntity>

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<SourceEntity> {
    override var entitySource: EntitySource
    var data: String
    var children: List<ChildSourceEntity.Builder>
  }

  companion object : EntityType<SourceEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      data: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
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
fun MutableEntityStorage.modifySourceEntity(
  entity: SourceEntity,
  modification: SourceEntity.Builder.() -> Unit,
): SourceEntity {
  return modifyEntity(SourceEntity.Builder::class.java, entity, modification)
}
//endregion

interface ChildSourceEntity : WorkspaceEntity {
  val data: String
  val parentEntity: SourceEntity

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ChildSourceEntity> {
    override var entitySource: EntitySource
    var data: String
    var parentEntity: SourceEntity.Builder
  }

  companion object : EntityType<ChildSourceEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      data: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
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
fun MutableEntityStorage.modifyChildSourceEntity(
  entity: ChildSourceEntity,
  modification: ChildSourceEntity.Builder.() -> Unit,
): ChildSourceEntity {
  return modifyEntity(ChildSourceEntity.Builder::class.java, entity, modification)
}
//endregion

interface SymbolicIdEntity : WorkspaceEntityWithSymbolicId {
  val data: String
  override val symbolicId: LinkedListEntityId
    get() {
      return LinkedListEntityId(data)
    }

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<SymbolicIdEntity> {
    override var entitySource: EntitySource
    var data: String
  }

  companion object : EntityType<SymbolicIdEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      data: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
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
fun MutableEntityStorage.modifySymbolicIdEntity(
  entity: SymbolicIdEntity,
  modification: SymbolicIdEntity.Builder.() -> Unit,
): SymbolicIdEntity {
  return modifyEntity(SymbolicIdEntity.Builder::class.java, entity, modification)
}
//endregion
