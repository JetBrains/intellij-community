// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity

interface NullToNotNullEntity: WorkspaceEntity {
  val nullString: String?
  val notNullBoolean: Boolean
  val notNullInt: Int

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<NullToNotNullEntity> {
    override var entitySource: EntitySource
    var nullString: String?
    var notNullBoolean: Boolean
    var notNullInt: Int
  }

  companion object : EntityType<NullToNotNullEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      notNullBoolean: Boolean,
      notNullInt: Int,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
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
fun MutableEntityStorage.modifyNullToNotNullEntity(
  entity: NullToNotNullEntity,
  modification: NullToNotNullEntity.Builder.() -> Unit,
): NullToNotNullEntity {
  return modifyEntity(NullToNotNullEntity.Builder::class.java, entity, modification)
}
//endregion
