// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.currentVersion

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList

interface ComputablePropEntity: WorkspaceEntity {
  val list: List<Map<List<Int?>, String>>
  val value: Int
  val computableText: String // Change is here, property is not computable

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : ComputablePropEntity, WorkspaceEntity.Builder<ComputablePropEntity> {
    override var entitySource: EntitySource
    override var list: MutableList<Map<List<Int?>, String>>
    override var value: Int
    override var computableText: String
  }

  companion object : EntityType<ComputablePropEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(list: List<Map<List<Int?>, String>>,
                        value: Int,
                        computableText: String,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): ComputablePropEntity {
      val builder = builder()
      builder.list = list.toMutableWorkspaceList()
      builder.value = value
      builder.computableText = computableText
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: ComputablePropEntity,
                                      modification: ComputablePropEntity.Builder.() -> Unit): ComputablePropEntity = modifyEntity(
  ComputablePropEntity.Builder::class.java, entity, modification)
//endregion
