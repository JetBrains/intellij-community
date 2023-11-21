// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.currentVersion

import com.intellij.platform.workspace.storage.*

// In this test we can deserialize cache
interface EnumPropsEntity: WorkspaceEntity {
  val someEnum: com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.EnumPropsEnum

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : EnumPropsEntity, WorkspaceEntity.Builder<EnumPropsEntity> {
    override var entitySource: EntitySource
    override var someEnum: EnumPropsEnum
  }

  companion object : EntityType<EnumPropsEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(someEnum: EnumPropsEnum, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): EnumPropsEntity {
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
fun MutableEntityStorage.modifyEntity(entity: EnumPropsEntity,
                                      modification: EnumPropsEntity.Builder.() -> Unit): EnumPropsEntity = modifyEntity(
  EnumPropsEntity.Builder::class.java, entity, modification)
//endregion


enum class EnumPropsEnum(val value: Int) {
  FIRST(value = 5) {
    val text: String = "first"
  },

  SECOND(value = 10) {
    val list: List<String> = emptyList()
  },

  THIRD(value = 9) {
    val set: Set<String> = setOf("1", "2", "3") //Change is here, Set<Int> --> Set<String>
  }
}