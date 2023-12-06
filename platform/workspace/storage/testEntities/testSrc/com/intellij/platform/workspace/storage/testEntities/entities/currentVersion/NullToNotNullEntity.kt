// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.currentVersion

import com.intellij.platform.workspace.storage.*

interface NullToNotNullEntity: WorkspaceEntity {
  val nullString: String // Change is here, string is not null
  val notNullBoolean: Boolean
  val notNullInt: Int

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : NullToNotNullEntity, WorkspaceEntity.Builder<NullToNotNullEntity> {
    override var entitySource: EntitySource
    override var nullString: String
    override var notNullBoolean: Boolean
    override var notNullInt: Int
  }

  companion object : EntityType<NullToNotNullEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(nullString: String,
                        notNullBoolean: Boolean,
                        notNullInt: Int,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): NullToNotNullEntity {
      val builder = builder()
      builder.nullString = nullString
      builder.notNullBoolean = notNullBoolean
      builder.notNullInt = notNullInt
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: NullToNotNullEntity,
                                      modification: NullToNotNullEntity.Builder.() -> Unit): NullToNotNullEntity = modifyEntity(
  NullToNotNullEntity.Builder::class.java, entity, modification)
//endregion
