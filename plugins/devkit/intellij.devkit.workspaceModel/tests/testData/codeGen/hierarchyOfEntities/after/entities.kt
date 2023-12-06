package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.annotations.Open

@Abstract
interface GrandParentEntity : WorkspaceEntity {
  val data1: String

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder<T : GrandParentEntity> : GrandParentEntity, WorkspaceEntity.Builder<T> {
    override var entitySource: EntitySource
    override var data1: String
  }

  companion object : EntityType<GrandParentEntity, Builder<GrandParentEntity>>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(data1: String,
                        entitySource: EntitySource,
                        init: (Builder<GrandParentEntity>.() -> Unit)? = null): GrandParentEntity {
      val builder = builder()
      builder.data1 = data1
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

@Open
interface ParentEntity : GrandParentEntity {
  val data2: String

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder<T : ParentEntity> : ParentEntity, GrandParentEntity.Builder<T>, WorkspaceEntity.Builder<T> {
    override var entitySource: EntitySource
    override var data1: String
    override var data2: String
  }

  companion object : EntityType<ParentEntity, Builder<ParentEntity>>(GrandParentEntity) {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(data1: String,
                        data2: String,
                        entitySource: EntitySource,
                        init: (Builder<ParentEntity>.() -> Unit)? = null): ParentEntity {
      val builder = builder()
      builder.data1 = data1
      builder.data2 = data2
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

interface ChildEntity: ParentEntity {
  val data3: String

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : ChildEntity, ParentEntity.Builder<ChildEntity>, WorkspaceEntity.Builder<ChildEntity> {
    override var entitySource: EntitySource
    override var data1: String
    override var data2: String
    override var data3: String
  }

  companion object : EntityType<ChildEntity, Builder>(ParentEntity) {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(data1: String,
                        data2: String,
                        data3: String,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): ChildEntity {
      val builder = builder()
      builder.data1 = data1
      builder.data2 = data2
      builder.data3 = data3
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: ChildEntity, modification: ChildEntity.Builder.() -> Unit) = modifyEntity(
  ChildEntity.Builder::class.java, entity, modification)
//endregion
