package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.MutableEntityStorage
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child



interface ParentEntity : WorkspaceEntity {
  val parentData: String

  @Child
  val child: ChildEntity?

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : ParentEntity, ModifiableWorkspaceEntity<ParentEntity>, ObjBuilder<ParentEntity> {
    override var parentData: String
    override var entitySource: EntitySource
    override var child: ChildEntity?
  }

  companion object : Type<ParentEntity, Builder>() {
    operator fun invoke(parentData: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): ParentEntity {
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
fun MutableEntityStorage.modifyEntity(entity: ParentEntity, modification: ParentEntity.Builder.() -> Unit) = modifyEntity(
  ParentEntity.Builder::class.java, entity, modification)
//endregion

interface ChildEntity : WorkspaceEntity {
  val childData: String

  //    override val parent: ParentEntity
  val parentEntity: ParentEntity

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : ChildEntity, ModifiableWorkspaceEntity<ChildEntity>, ObjBuilder<ChildEntity> {
    override var childData: String
    override var entitySource: EntitySource
    override var parentEntity: ParentEntity
  }

  companion object : Type<ChildEntity, Builder>() {
    operator fun invoke(childData: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): ChildEntity {
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
fun MutableEntityStorage.modifyEntity(entity: ChildEntity, modification: ChildEntity.Builder.() -> Unit) = modifyEntity(
  ChildEntity.Builder::class.java, entity, modification)
//endregion
