package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.storage.annotations.Child

interface ReferredEntity : WorkspaceEntity {
  val version: Int
  val name: String
  val contentRoot: @Child ContentRootEntity?

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : ReferredEntity, WorkspaceEntity.Builder<ReferredEntity> {
    override var entitySource: EntitySource
    override var version: Int
    override var name: String
    override var contentRoot: ContentRootEntity?
  }

  companion object : EntityType<ReferredEntity, Builder>() {
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