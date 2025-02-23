// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.currentVersion

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList


interface ChangedPropsOrderEntity: WorkspaceEntity {
  val version: Int
  val string: String
  val data: com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ChangedPropsOrderDataClass // Change is here, order of entities is changed
  val list: List<Set<Int>>

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ChangedPropsOrderEntity> {
    override var entitySource: EntitySource
    var version: Int
    var string: String
    var data: ChangedPropsOrderDataClass
    var list: MutableList<Set<Int>>
  }

  companion object : EntityType<ChangedPropsOrderEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      version: Int,
      string: String,
      data: ChangedPropsOrderDataClass,
      list: List<Set<Int>>,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.version = version
      builder.string = string
      builder.data = data
      builder.list = list.toMutableWorkspaceList()
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