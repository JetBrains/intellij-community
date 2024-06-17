// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.currentVersion

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity

// In this test we can deserialize cache
interface SubsetEnumEntity: WorkspaceEntity {
  val someEnum: com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SubsetEnumEnum

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<SubsetEnumEntity> {
    override var entitySource: EntitySource
    var someEnum: SubsetEnumEnum
  }

  companion object : EntityType<SubsetEnumEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      someEnum: SubsetEnumEnum,
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
fun MutableEntityStorage.modifySubsetEnumEntity(
  entity: SubsetEnumEntity,
  modification: SubsetEnumEntity.Builder.() -> Unit,
): SubsetEnumEntity {
  return modifyEntity(SubsetEnumEntity.Builder::class.java, entity, modification)
}
//endregion

enum class SubsetEnumEnum(val type: String) {
  FIRST(type = "first"), SECOND(type = "second"), THIRD(type = "third"),
  FOURTH(type = "fourth"), FIFTH(type = "fifth")
}