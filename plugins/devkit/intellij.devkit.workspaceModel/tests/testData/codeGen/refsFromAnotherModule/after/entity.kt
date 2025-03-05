package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Child

interface ReferredEntity : WorkspaceEntity {
  val version: Int
  val name: String
  val contentRoot: @Child ContentRootEntity?

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ReferredEntity> {
    override var entitySource: EntitySource
    var version: Int
    var name: String
    var contentRoot: ContentRootEntity.Builder?
  }

  companion object : EntityType<ReferredEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      version: Int,
      name: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
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
fun MutableEntityStorage.modifyReferredEntity(
  entity: ReferredEntity,
  modification: ReferredEntity.Builder.() -> Unit,
): ReferredEntity {
  return modifyEntity(ReferredEntity.Builder::class.java, entity, modification)
}

var ContentRootEntity.Builder.ref: ReferredEntity.Builder
  by WorkspaceEntity.extensionBuilder(ReferredEntity::class.java)
//endregion

val ContentRootEntity.ref: ReferredEntity
  by WorkspaceEntity.extension()