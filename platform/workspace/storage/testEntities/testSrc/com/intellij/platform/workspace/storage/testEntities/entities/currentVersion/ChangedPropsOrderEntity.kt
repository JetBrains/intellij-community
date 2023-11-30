// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.currentVersion

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList


interface ChangedPropsOrderEntity: WorkspaceEntity {
  val version: Int
  val string: String
  val data: com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ChangedPropsOrderDataClass // Change is here, order of entities is changed
  val list: List<Set<Int>>

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : ChangedPropsOrderEntity, WorkspaceEntity.Builder<ChangedPropsOrderEntity> {
    override var entitySource: EntitySource
    override var version: Int
    override var string: String
    override var data: ChangedPropsOrderDataClass
    override var list: MutableList<Set<Int>>
  }

  companion object : EntityType<ChangedPropsOrderEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(version: Int,
                        string: String,
                        data: ChangedPropsOrderDataClass,
                        list: List<Set<Int>>,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): ChangedPropsOrderEntity {
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
fun MutableEntityStorage.modifyEntity(entity: ChangedPropsOrderEntity,
                                      modification: ChangedPropsOrderEntity.Builder.() -> Unit): ChangedPropsOrderEntity = modifyEntity(
  ChangedPropsOrderEntity.Builder::class.java, entity, modification)
//endregion

data class ChangedPropsOrderDataClass(val value: Int, val text: String)