// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.WorkspaceEntity
import org.jetbrains.deft.annotations.Child
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion

import com.intellij.workspaceModel.storage.MutableEntityStorage
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type


interface TreeEntity : WorkspaceEntity {
  val data: String

  val children: List<@Child TreeEntity>
  val parentEntity: TreeEntity

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : TreeEntity, WorkspaceEntity.Builder<TreeEntity>, ObjBuilder<TreeEntity> {
    override var entitySource: EntitySource
    override var data: String
    override var children: List<TreeEntity>
    override var parentEntity: TreeEntity
  }

  companion object : Type<TreeEntity, Builder>() {
    operator fun invoke(data: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): TreeEntity {
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
fun MutableEntityStorage.modifyEntity(entity: TreeEntity, modification: TreeEntity.Builder.() -> Unit) = modifyEntity(
  TreeEntity.Builder::class.java, entity, modification)
//endregion
