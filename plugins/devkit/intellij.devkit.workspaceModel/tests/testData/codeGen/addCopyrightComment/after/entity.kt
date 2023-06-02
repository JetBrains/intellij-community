//some copyright comment
package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspaceModel.storage.EntitySource
import com.intellij.platform.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspaceModel.storage.MutableEntityStorage
import com.intellij.platform.workspaceModel.storage.WorkspaceEntity
import com.intellij.platform.workspaceModel.storage.ObjBuilder
import com.intellij.platform.workspaceModel.storage.Type

interface SimpleEntity : WorkspaceEntity {
  val name: String

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : SimpleEntity, WorkspaceEntity.Builder<SimpleEntity>, ObjBuilder<SimpleEntity> {
    override var entitySource: EntitySource
    override var name: String
  }

  companion object : Type<SimpleEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(name: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): SimpleEntity {
      val builder = builder()
      builder.name = name
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: SimpleEntity, modification: SimpleEntity.Builder.() -> Unit) = modifyEntity(
  SimpleEntity.Builder::class.java, entity, modification)
//endregion
