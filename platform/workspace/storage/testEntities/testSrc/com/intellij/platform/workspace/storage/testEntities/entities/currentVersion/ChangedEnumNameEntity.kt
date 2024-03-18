// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.currentVersion

import com.intellij.platform.workspace.storage.*

interface ChangedEnumNameEntity: WorkspaceEntity {
  val someEnum: com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ChangedEnumNameEnum

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : ChangedEnumNameEntity, WorkspaceEntity.Builder<ChangedEnumNameEntity> {
    override var entitySource: EntitySource
    override var someEnum: ChangedEnumNameEnum
  }

  companion object : EntityType<ChangedEnumNameEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(someEnum: ChangedEnumNameEnum,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): ChangedEnumNameEntity {
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
fun MutableEntityStorage.modifyEntity(entity: ChangedEnumNameEntity,
                                      modification: ChangedEnumNameEntity.Builder.() -> Unit): ChangedEnumNameEntity = modifyEntity(
  ChangedEnumNameEntity.Builder::class.java, entity, modification)
//endregion

enum class ChangedEnumNameEnum {
  FIRST, SECOND, NOT_THIRD // Change is here, new name of the third enum entry
}