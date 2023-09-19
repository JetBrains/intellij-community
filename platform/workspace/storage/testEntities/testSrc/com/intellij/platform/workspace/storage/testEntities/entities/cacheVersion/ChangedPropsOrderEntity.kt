// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  @GeneratedCodeApiVersion(2)
  interface Builder : ChangedPropsOrderEntity, WorkspaceEntity.Builder<ChangedPropsOrderEntity> {
    override var entitySource: EntitySource
    override var version: Int
    override var string: String
    override var list: MutableList<Set<Int>>
    override var data: ChangedPropsOrderDataClass
  }

  companion object : EntityType<ChangedPropsOrderEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(version: Int,
                        string: String,
                        list: List<Set<Int>>,
                        data: ChangedPropsOrderDataClass,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): ChangedPropsOrderEntity {
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
fun MutableEntityStorage.modifyEntity(entity: ChangedPropsOrderEntity,
                                      modification: ChangedPropsOrderEntity.Builder.() -> Unit): ChangedPropsOrderEntity = modifyEntity(
  ChangedPropsOrderEntity.Builder::class.java, entity, modification)
//endregion

data class ChangedPropsOrderDataClass(val value: Int, val text: String)