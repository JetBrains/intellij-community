package com.intellij.workspaceModel.test.api

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Abstract
import org.jetbrains.deft.annotations.Open

@Abstract
interface GrandParentEntity : WorkspaceEntity {
  val data1: String

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder<T : GrandParentEntity> : GrandParentEntity, ModifiableWorkspaceEntity<T>, ObjBuilder<T> {
    override var data1: String
    override var entitySource: EntitySource
  }

  companion object : Type<GrandParentEntity, Builder<GrandParentEntity>>() {
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

interface ParentEntity : GrandParentEntity {
  val data2: String

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : ParentEntity, GrandParentEntity.Builder<ParentEntity>, ModifiableWorkspaceEntity<ParentEntity>, ObjBuilder<ParentEntity> {
    override var data1: String
    override var data2: String
    override var entitySource: EntitySource
  }

  companion object : Type<ParentEntity, Builder>(GrandParentEntity) {
    operator fun invoke(data1: String, data2: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): ParentEntity {
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

//region generated code
fun MutableEntityStorage.modifyEntity(entity: ParentEntity, modification: ParentEntity.Builder.() -> Unit) = modifyEntity(
  ParentEntity.Builder::class.java, entity, modification)
//endregion

interface ChildEntity: ParentEntity {
  val data3: String

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : ChildEntity, ParentEntity.Builder<ChildEntity>, ModifiableWorkspaceEntity<ChildEntity>, ObjBuilder<ChildEntity> {
    override var data1: String
    override var data2: String
    override var data3: String
    override var entitySource: EntitySource
  }

  companion object : Type<ChildEntity, Builder>(ParentEntity) {
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
