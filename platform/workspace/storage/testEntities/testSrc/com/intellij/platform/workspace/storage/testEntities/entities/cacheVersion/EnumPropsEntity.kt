// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity

// In this test we can deserialize cache
interface EnumPropsEntity: WorkspaceEntity {
  val someEnum: com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.EnumPropsEnum

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<EnumPropsEntity> {
    override var entitySource: EntitySource
    var someEnum: EnumPropsEnum
  }

  companion object : EntityType<EnumPropsEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      someEnum: EnumPropsEnum,
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
fun MutableEntityStorage.modifyEnumPropsEntity(
  entity: EnumPropsEntity,
  modification: EnumPropsEntity.Builder.() -> Unit,
): EnumPropsEntity {
  return modifyEntity(EnumPropsEntity.Builder::class.java, entity, modification)
}
//endregion

enum class EnumPropsEnum(val value: Int) {
  FIRST(value = 5) {
    val text: String = "first"
  },

  SECOND(value = 10) {
    val list: List<String> = emptyList()
  },

  THIRD(value = 9) {
    val set: Set<Int> = setOf(1, 2, 3)
  }
}