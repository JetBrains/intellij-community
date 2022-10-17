// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.entities.unknowntypes.test.api

import com.intellij.workspaceModel.storage.WorkspaceEntity
import java.util.Date
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.MutableEntityStorage
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type


interface UnknownFieldEntity : WorkspaceEntity {
  val data: Date

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : UnknownFieldEntity, WorkspaceEntity.Builder<UnknownFieldEntity>, ObjBuilder<UnknownFieldEntity> {
    override var data: Date
    override var entitySource: EntitySource
  }

  companion object : Type<UnknownFieldEntity, Builder>() {
    operator fun invoke(data: Date, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): UnknownFieldEntity {
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
fun MutableEntityStorage.modifyEntity(entity: UnknownFieldEntity, modification: UnknownFieldEntity.Builder.() -> Unit) = modifyEntity(
  UnknownFieldEntity.Builder::class.java, entity, modification)
//endregion
