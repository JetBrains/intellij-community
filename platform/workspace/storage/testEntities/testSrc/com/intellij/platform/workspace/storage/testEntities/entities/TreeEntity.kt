// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.WorkspaceEntity
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion

import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.EntityType


interface TreeEntity : WorkspaceEntity {
  val data: String

  val children: List<@Child TreeEntity>
  val parentEntity: TreeEntity?

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : TreeEntity, WorkspaceEntity.Builder<TreeEntity> {
    override var entitySource: EntitySource
    override var data: String
    override var children: List<TreeEntity>
    override var parentEntity: TreeEntity?
  }

  companion object : EntityType<TreeEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
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
