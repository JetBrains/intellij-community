// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.currentVersion

import com.intellij.platform.workspace.storage.*

// In this test we can deserialize cache
interface SubsetEnumEntity: WorkspaceEntity {
  val someEnum: com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SubsetEnumEnum

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : SubsetEnumEntity, WorkspaceEntity.Builder<SubsetEnumEntity> {
    override var entitySource: EntitySource
    override var someEnum: SubsetEnumEnum
  }

  companion object : EntityType<SubsetEnumEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(someEnum: SubsetEnumEnum, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): SubsetEnumEntity {
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
fun MutableEntityStorage.modifyEntity(entity: SubsetEnumEntity,
                                      modification: SubsetEnumEntity.Builder.() -> Unit): SubsetEnumEntity = modifyEntity(
  SubsetEnumEntity.Builder::class.java, entity, modification)
//endregion

enum class SubsetEnumEnum(val type: String) {
  FIRST(type = "first"), SECOND(type = "second"), THIRD(type = "third"),
  FOURTH(type = "fourth"), FIFTH(type = "fifth")
}