package com.intellij.workspaceModel.test.api

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ContentRootEntity
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child

interface ReferredEntity : WorkspaceEntity {
  val version: Int
  val name: String
  val contentRoot: @Child ContentRootEntity?

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : ReferredEntity, WorkspaceEntity.Builder<ReferredEntity>, ObjBuilder<ReferredEntity> {
    override var entitySource: EntitySource
    override var version: Int
    override var name: String
    override var contentRoot: ContentRootEntity?
  }

  companion object : Type<ReferredEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(version: Int, name: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): ReferredEntity {
      val builder = builder()
      builder.version = version
      builder.name = name
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: ReferredEntity, modification: ReferredEntity.Builder.() -> Unit) = modifyEntity(
  ReferredEntity.Builder::class.java, entity, modification)

var ContentRootEntity.Builder.ref: ReferredEntity
  by WorkspaceEntity.extension()
//endregion

val ContentRootEntity.ref: ReferredEntity
  by WorkspaceEntity.extension()