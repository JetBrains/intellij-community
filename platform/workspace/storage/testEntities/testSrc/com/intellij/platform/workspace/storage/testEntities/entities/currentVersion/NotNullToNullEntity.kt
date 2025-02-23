// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.currentVersion

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList

// In this test we can deserialize cache
interface NotNullToNullEntity: WorkspaceEntity {
  val nullInt: Int?
  val notNullString: String? //Change is here, property is nullable
  val notNullList: List<Int>

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<NotNullToNullEntity> {
    override var entitySource: EntitySource
    var nullInt: Int?
    var notNullString: String?
    var notNullList: MutableList<Int>
  }

  companion object : EntityType<NotNullToNullEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      notNullList: List<Int>,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
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
fun MutableEntityStorage.modifyNotNullToNullEntity(
  entity: NotNullToNullEntity,
  modification: NotNullToNullEntity.Builder.() -> Unit,
): NotNullToNullEntity {
  return modifyEntity(NotNullToNullEntity.Builder::class.java, entity, modification)
}
//endregion
