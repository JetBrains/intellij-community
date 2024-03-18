// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.currentVersion

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList

// In this test we can deserialize cache
interface NotNullToNullEntity: WorkspaceEntity {
  val nullInt: Int?
  val notNullString: String? //Change is here, property is nullable
  val notNullList: List<Int>

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : NotNullToNullEntity, WorkspaceEntity.Builder<NotNullToNullEntity> {
    override var entitySource: EntitySource
    override var nullInt: Int?
    override var notNullString: String?
    override var notNullList: MutableList<Int>
  }

  companion object : EntityType<NotNullToNullEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(notNullList: List<Int>, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): NotNullToNullEntity {
      val builder = builder()
      builder.notNullList = notNullList.toMutableWorkspaceList()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: NotNullToNullEntity,
                                      modification: NotNullToNullEntity.Builder.() -> Unit): NotNullToNullEntity = modifyEntity(
  NotNullToNullEntity.Builder::class.java, entity, modification)
//endregion
