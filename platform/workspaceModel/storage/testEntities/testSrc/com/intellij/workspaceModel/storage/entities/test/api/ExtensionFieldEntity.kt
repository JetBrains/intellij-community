package com.intellij.workspaceModel.storage.entities.test.api

import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion

import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity



interface MainEntity : WorkspaceEntity {
  val x: String

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : MainEntity, WorkspaceEntity.Builder<MainEntity>, ObjBuilder<MainEntity> {
    override var entitySource: EntitySource
    override var x: String
  }

  companion object : Type<MainEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(x: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): MainEntity {
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
fun MutableEntityStorage.modifyEntity(entity: MainEntity, modification: MainEntity.Builder.() -> Unit) = modifyEntity(
  MainEntity.Builder::class.java, entity, modification)

var MainEntity.Builder.child: @Child AttachedEntity?
  by WorkspaceEntity.extension()
//endregion

interface AttachedEntity : WorkspaceEntity {
  val ref: MainEntity
  val data: String

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : AttachedEntity, WorkspaceEntity.Builder<AttachedEntity>, ObjBuilder<AttachedEntity> {
    override var entitySource: EntitySource
    override var ref: MainEntity
    override var data: String
  }

  companion object : Type<AttachedEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(data: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): AttachedEntity {
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
fun MutableEntityStorage.modifyEntity(entity: AttachedEntity, modification: AttachedEntity.Builder.() -> Unit) = modifyEntity(
  AttachedEntity.Builder::class.java, entity, modification)
//endregion

val MainEntity.child: @Child AttachedEntity?
    by WorkspaceEntity.extension()
