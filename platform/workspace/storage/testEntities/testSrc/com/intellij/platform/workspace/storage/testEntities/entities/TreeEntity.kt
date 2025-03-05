// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Child


interface TreeEntity : WorkspaceEntity {
  val data: String

  val children: List<@Child TreeEntity>
  val parentEntity: TreeEntity?

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<TreeEntity> {
    override var entitySource: EntitySource
    var data: String
    var children: List<TreeEntity.Builder>
    var parentEntity: TreeEntity.Builder?
  }

  companion object : EntityType<TreeEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      data: String,
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
fun MutableEntityStorage.modifyTreeEntity(
  entity: TreeEntity,
  modification: TreeEntity.Builder.() -> Unit,
): TreeEntity {
  return modifyEntity(TreeEntity.Builder::class.java, entity, modification)
}
//endregion
