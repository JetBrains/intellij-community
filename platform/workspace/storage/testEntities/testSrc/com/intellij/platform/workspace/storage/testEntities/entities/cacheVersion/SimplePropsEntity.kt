// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceSet

interface SimplePropsEntity: WorkspaceEntity {
  val text: String
  val list: List<Int>
  val set: Set<List<String>>
  val map: Map<Set<Int>, List<String>>
  val bool: Boolean

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<SimplePropsEntity> {
    override var entitySource: EntitySource
    var text: String
    var list: MutableList<Int>
    var set: MutableSet<List<String>>
    var map: Map<Set<Int>, List<String>>
    var bool: Boolean
  }

  companion object : EntityType<SimplePropsEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      text: String,
      list: List<Int>,
      set: Set<List<String>>,
      map: Map<Set<Int>, List<String>>,
      bool: Boolean,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.text = text
      builder.list = list.toMutableWorkspaceList()
      builder.set = set.toMutableWorkspaceSet()
      builder.map = map
      builder.bool = bool
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifySimplePropsEntity(
  entity: SimplePropsEntity,
  modification: SimplePropsEntity.Builder.() -> Unit,
): SimplePropsEntity {
  return modifyEntity(SimplePropsEntity.Builder::class.java, entity, modification)
}
//endregion
