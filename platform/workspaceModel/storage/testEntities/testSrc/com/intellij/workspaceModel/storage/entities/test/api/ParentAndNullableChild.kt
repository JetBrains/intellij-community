package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.WorkspaceEntity
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child
import com.intellij.workspaceModel.storage.MutableEntityStorage



interface ParentNullableEntity : WorkspaceEntity {
  val parentData: String

  @Child
  val child: ChildNullableEntity?


  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(1)
  interface Builder: ParentNullableEntity, ModifiableWorkspaceEntity<ParentNullableEntity>, ObjBuilder<ParentNullableEntity> {
      override var parentData: String
      override var entitySource: EntitySource
      override var child: ChildNullableEntity?
  }
  
  companion object: Type<ParentNullableEntity, Builder>() {
      operator fun invoke(parentData: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): ParentNullableEntity {
          val builder = builder()
          builder.parentData = parentData
          builder.entitySource = entitySource
          init?.invoke(builder)
          return builder
      }
  }
  //@formatter:on
  //endregion

}
//region generated code
fun MutableEntityStorage.modifyEntity(entity: ParentNullableEntity, modification: ParentNullableEntity.Builder.() -> Unit) = modifyEntity(ParentNullableEntity.Builder::class.java, entity, modification)
//endregion

interface ChildNullableEntity : WorkspaceEntity {
  val childData: String

  val parentEntity: ParentNullableEntity


  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(1)
  interface Builder: ChildNullableEntity, ModifiableWorkspaceEntity<ChildNullableEntity>, ObjBuilder<ChildNullableEntity> {
      override var childData: String
      override var entitySource: EntitySource
      override var parentEntity: ParentNullableEntity
  }
  
  companion object: Type<ChildNullableEntity, Builder>() {
      operator fun invoke(childData: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): ChildNullableEntity {
          val builder = builder()
          builder.childData = childData
          builder.entitySource = entitySource
          init?.invoke(builder)
          return builder
      }
  }
  //@formatter:on
  //endregion

}
//region generated code
fun MutableEntityStorage.modifyEntity(entity: ChildNullableEntity, modification: ChildNullableEntity.Builder.() -> Unit) = modifyEntity(ChildNullableEntity.Builder::class.java, entity, modification)
//endregion
