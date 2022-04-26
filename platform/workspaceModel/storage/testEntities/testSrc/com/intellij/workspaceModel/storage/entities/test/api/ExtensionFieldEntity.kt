package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.*
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity



interface MainEntity : WorkspaceEntity {
  val x: String


  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: MainEntity, ModifiableWorkspaceEntity<MainEntity>, ObjBuilder<MainEntity> {
      override var x: String
      override var entitySource: EntitySource
  }
  
  companion object: Type<MainEntity, Builder>() {
      operator fun invoke(x: String, entitySource: EntitySource, init: Builder.() -> Unit): MainEntity {
          val builder = builder(init)
          builder.x = x
          builder.entitySource = entitySource
          return builder
      }
  }
  //@formatter:on
  //endregion

}

interface AttachedEntity : WorkspaceEntity {
  val ref: MainEntity
  val data: String


  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: AttachedEntity, ModifiableWorkspaceEntity<AttachedEntity>, ObjBuilder<AttachedEntity> {
      override var ref: MainEntity
      override var entitySource: EntitySource
      override var data: String
  }
  
  companion object: Type<AttachedEntity, Builder>() {
      operator fun invoke(entitySource: EntitySource, data: String, init: Builder.() -> Unit): AttachedEntity {
          val builder = builder(init)
          builder.entitySource = entitySource
          builder.data = data
          return builder
      }
  }
  //@formatter:on
  //endregion

}

val MainEntity.child: @Child AttachedEntity?
  get() = referrersx(AttachedEntity::ref).singleOrNull()
