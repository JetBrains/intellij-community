package com.intellij.workspaceModel.storage.entities.test.api

import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity



interface MainEntityToParent : WorkspaceEntity {
  val child: @Child AttachedEntityToParent?
  val x: String


  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(1)
  interface Builder: MainEntityToParent, ModifiableWorkspaceEntity<MainEntityToParent>, ObjBuilder<MainEntityToParent> {
      override var child: AttachedEntityToParent?
      override var entitySource: EntitySource
      override var x: String
  }
  
  companion object: Type<MainEntityToParent, Builder>() {
      operator fun invoke(entitySource: EntitySource, x: String, init: (Builder.() -> Unit)? = null): MainEntityToParent {
          val builder = builder()
          builder.entitySource = entitySource
          builder.x = x
          init?.invoke(builder)
          return builder
      }
  }
  //@formatter:on
  //endregion

}
//region generated code
fun MutableEntityStorage.modifyEntity(entity: MainEntityToParent, modification: MainEntityToParent.Builder.() -> Unit) = modifyEntity(MainEntityToParent.Builder::class.java, entity, modification)
//endregion

interface AttachedEntityToParent : WorkspaceEntity {
  val data: String


  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(1)
  interface Builder: AttachedEntityToParent, ModifiableWorkspaceEntity<AttachedEntityToParent>, ObjBuilder<AttachedEntityToParent> {
      override var data: String
      override var entitySource: EntitySource
  }
  
  companion object: Type<AttachedEntityToParent, Builder>() {
      operator fun invoke(data: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): AttachedEntityToParent {
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
fun MutableEntityStorage.modifyEntity(entity: AttachedEntityToParent, modification: AttachedEntityToParent.Builder.() -> Unit) = modifyEntity(AttachedEntityToParent.Builder::class.java, entity, modification)
var AttachedEntityToParent.Builder.ref: MainEntityToParent
    by WorkspaceEntity.extension()

//endregion

val AttachedEntityToParent.ref: MainEntityToParent
    by WorkspaceEntity.extension()
