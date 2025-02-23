// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList

interface ComputablePropEntity: WorkspaceEntity {
  val list: List<Map<List<Int?>, String>>
  val value: Int
  val computableText: String
    get() = "somePrefix${value}someSuffix"

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ComputablePropEntity> {
    override var entitySource: EntitySource
    var list: MutableList<Map<List<Int?>, String>>
    var value: Int
  }

  companion object : EntityType<ComputablePropEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      list: List<Map<List<Int?>, String>>,
      value: Int,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.list = list.toMutableWorkspaceList()
      builder.value = value
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyComputablePropEntity(
  entity: ComputablePropEntity,
  modification: ComputablePropEntity.Builder.() -> Unit,
): ComputablePropEntity {
  return modifyEntity(ComputablePropEntity.Builder::class.java, entity, modification)
}
//endregion
