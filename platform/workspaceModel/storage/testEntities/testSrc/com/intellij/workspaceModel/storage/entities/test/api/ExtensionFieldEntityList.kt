package com.intellij.workspaceModel.storage.entities.test.api

import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity



interface MainEntityList : WorkspaceEntity {
  val x: String

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : MainEntityList, WorkspaceEntity.Builder<MainEntityList>, ObjBuilder<MainEntityList> {
    override var entitySource: EntitySource
    override var x: String
  }

  companion object : Type<MainEntityList, Builder>() {
    operator fun invoke(x: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): MainEntityList {
      val builder = builder()
      builder.x = x
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: MainEntityList, modification: MainEntityList.Builder.() -> Unit) = modifyEntity(
  MainEntityList.Builder::class.java, entity, modification)

var MainEntityList.Builder.child: @Child List<AttachedEntityList>
  by WorkspaceEntity.extension()
//endregion

interface AttachedEntityList : WorkspaceEntity {
  val ref: MainEntityList?
  val data: String

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : AttachedEntityList, WorkspaceEntity.Builder<AttachedEntityList>, ObjBuilder<AttachedEntityList> {
    override var entitySource: EntitySource
    override var ref: MainEntityList?
    override var data: String
  }

  companion object : Type<AttachedEntityList, Builder>() {
    operator fun invoke(data: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): AttachedEntityList {
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
fun MutableEntityStorage.modifyEntity(entity: AttachedEntityList, modification: AttachedEntityList.Builder.() -> Unit) = modifyEntity(
  AttachedEntityList.Builder::class.java, entity, modification)
//endregion

val MainEntityList.child: List<@Child AttachedEntityList>
    by WorkspaceEntity.extension()
