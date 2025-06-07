// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity

interface ChangedEnumNameEntity: WorkspaceEntity {
  val someEnum: com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ChangedEnumNameEnum

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ChangedEnumNameEntity> {
    override var entitySource: EntitySource
    var someEnum: ChangedEnumNameEnum
  }

  companion object : EntityType<ChangedEnumNameEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      someEnum: ChangedEnumNameEnum,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.someEnum = someEnum
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyChangedEnumNameEntity(
  entity: ChangedEnumNameEntity,
  modification: ChangedEnumNameEntity.Builder.() -> Unit,
): ChangedEnumNameEntity {
  return modifyEntity(ChangedEnumNameEntity.Builder::class.java, entity, modification)
}
//endregion

enum class ChangedEnumNameEnum {
  A_ENTRY, B_ENTRY, CA_ENTRY
}