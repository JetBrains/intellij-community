// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList


interface ChangedPropsOrderEntity: WorkspaceEntity {
  val version: Int
  val string: String
  val list: List<Set<Int>>
  val data: com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ChangedPropsOrderDataClass

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ChangedPropsOrderEntity> {
    override var entitySource: EntitySource
    var version: Int
    var string: String
    var list: MutableList<Set<Int>>
    var data: ChangedPropsOrderDataClass
  }

  companion object : EntityType<ChangedPropsOrderEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      version: Int,
      string: String,
      list: List<Set<Int>>,
      data: ChangedPropsOrderDataClass,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.version = version
      builder.string = string
      builder.list = list.toMutableWorkspaceList()
      builder.data = data
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyChangedPropsOrderEntity(
  entity: ChangedPropsOrderEntity,
  modification: ChangedPropsOrderEntity.Builder.() -> Unit,
): ChangedPropsOrderEntity {
  return modifyEntity(ChangedPropsOrderEntity.Builder::class.java, entity, modification)
}
//endregion

data class ChangedPropsOrderDataClass(val value: Int, val text: String)