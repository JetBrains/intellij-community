package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import java.util.Date
import com.intellij.platform.workspace.storage.ObjBuilder
import com.intellij.platform.workspace.storage.Type

interface UnknownPropertyTypeEntity : WorkspaceEntity {
  val date: Date

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : UnknownPropertyTypeEntity, WorkspaceEntity.Builder<UnknownPropertyTypeEntity>, ObjBuilder<UnknownPropertyTypeEntity> {
    override var entitySource: EntitySource
    override var date: Date
  }

  companion object : Type<UnknownPropertyTypeEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(date: Date, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): UnknownPropertyTypeEntity {
      val builder = builder()
      builder.date = date
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: UnknownPropertyTypeEntity,
                                      modification: UnknownPropertyTypeEntity.Builder.() -> Unit) = modifyEntity(
  UnknownPropertyTypeEntity.Builder::class.java, entity, modification)
//endregion
