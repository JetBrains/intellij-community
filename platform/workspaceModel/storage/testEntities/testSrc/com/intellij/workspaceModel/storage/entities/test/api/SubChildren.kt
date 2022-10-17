package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion

import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child



interface ParentSubEntity : WorkspaceEntity {
  val parentData: String

  @Child
  val child: ChildSubEntity?

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : ParentSubEntity, WorkspaceEntity.Builder<ParentSubEntity>, ObjBuilder<ParentSubEntity> {
    override var entitySource: EntitySource
    override var parentData: String
    override var child: ChildSubEntity?
  }

  companion object : Type<ParentSubEntity, Builder>() {
    operator fun invoke(parentData: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): ParentSubEntity {
      val builder = builder()
      builder.parentData = parentData
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: ParentSubEntity, modification: ParentSubEntity.Builder.() -> Unit) = modifyEntity(
  ParentSubEntity.Builder::class.java, entity, modification)
//endregion

interface ChildSubEntity : WorkspaceEntity {
  val parentEntity: ParentSubEntity

  @Child
  val child: ChildSubSubEntity?

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : ChildSubEntity, WorkspaceEntity.Builder<ChildSubEntity>, ObjBuilder<ChildSubEntity> {
    override var entitySource: EntitySource
    override var parentEntity: ParentSubEntity
    override var child: ChildSubSubEntity?
  }

  companion object : Type<ChildSubEntity, Builder>() {
    operator fun invoke(entitySource: EntitySource, init: (Builder.() -> Unit)? = null): ChildSubEntity {
      val builder = builder()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: ChildSubEntity, modification: ChildSubEntity.Builder.() -> Unit) = modifyEntity(
  ChildSubEntity.Builder::class.java, entity, modification)
//endregion

interface ChildSubSubEntity : WorkspaceEntity {
  val parentEntity: ChildSubEntity

  val childData: String

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : ChildSubSubEntity, WorkspaceEntity.Builder<ChildSubSubEntity>, ObjBuilder<ChildSubSubEntity> {
    override var entitySource: EntitySource
    override var parentEntity: ChildSubEntity
    override var childData: String
  }

  companion object : Type<ChildSubSubEntity, Builder>() {
    operator fun invoke(childData: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): ChildSubSubEntity {
      val builder = builder()
      builder.childData = childData
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: ChildSubSubEntity, modification: ChildSubSubEntity.Builder.() -> Unit) = modifyEntity(
  ChildSubSubEntity.Builder::class.java, entity, modification)
//endregion
