// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspaceModel.storage.testEntities.entities

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import java.util.Date
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import java.util.*


interface UnknownFieldEntity : WorkspaceEntity {
  val data: Date

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : UnknownFieldEntity, WorkspaceEntity.Builder<UnknownFieldEntity>, ObjBuilder<UnknownFieldEntity> {
    override var entitySource: EntitySource
    override var data: Date
  }

  companion object : Type<UnknownFieldEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
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
