// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Default


interface DefaultValueEntity: WorkspaceEntity {
  val name: String
  val isGenerated: Boolean
    @Default get() = true
  val anotherName: String
    @Default get() = "Another Text"

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<DefaultValueEntity> {
    override var entitySource: EntitySource
    var name: String
    var isGenerated: Boolean
    var anotherName: String
  }

  companion object : EntityType<DefaultValueEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      name: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
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
fun MutableEntityStorage.modifyDefaultValueEntity(
  entity: DefaultValueEntity,
  modification: DefaultValueEntity.Builder.() -> Unit,
): DefaultValueEntity {
  return modifyEntity(DefaultValueEntity.Builder::class.java, entity, modification)
}
//endregion
