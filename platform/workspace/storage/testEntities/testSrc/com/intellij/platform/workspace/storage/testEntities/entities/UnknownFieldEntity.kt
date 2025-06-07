// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import java.util.Date


interface UnknownFieldEntity : WorkspaceEntity {
  val data: Date

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<UnknownFieldEntity> {
    override var entitySource: EntitySource
    var data: Date
  }

  companion object : EntityType<UnknownFieldEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      data: Date,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
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
fun MutableEntityStorage.modifyUnknownFieldEntity(
  entity: UnknownFieldEntity,
  modification: UnknownFieldEntity.Builder.() -> Unit,
): UnknownFieldEntity {
  return modifyEntity(UnknownFieldEntity.Builder::class.java, entity, modification)
}
//endregion
