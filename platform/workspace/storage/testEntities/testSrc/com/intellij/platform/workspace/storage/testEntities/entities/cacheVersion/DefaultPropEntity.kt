// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Default
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList

// In this test we can deserialize cache
interface DefaultPropEntity: WorkspaceEntity {
  val someString: String
  val someList: List<Int>
  val constInt: Int
    @Default get() = 9

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : DefaultPropEntity, WorkspaceEntity.Builder<DefaultPropEntity> {
    override var entitySource: EntitySource
    override var someString: String
    override var someList: MutableList<Int>
    override var constInt: Int
  }

  companion object : EntityType<DefaultPropEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(someString: String,
                        someList: List<Int>,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): DefaultPropEntity {
      val builder = builder()
      builder.someString = someString
      builder.someList = someList.toMutableWorkspaceList()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: DefaultPropEntity,
                                      modification: DefaultPropEntity.Builder.() -> Unit): DefaultPropEntity = modifyEntity(
  DefaultPropEntity.Builder::class.java, entity, modification)
//endregion
