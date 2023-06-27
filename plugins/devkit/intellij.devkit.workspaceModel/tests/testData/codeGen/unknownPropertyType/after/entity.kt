package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import java.util.Date

interface UnknownPropertyTypeEntity : WorkspaceEntity {
  val date: Date

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : UnknownPropertyTypeEntity, WorkspaceEntity.Builder<UnknownPropertyTypeEntity> {
    override var entitySource: EntitySource
    override var date: Date
  }

  companion object : EntityType<UnknownPropertyTypeEntity, Builder>() {
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
