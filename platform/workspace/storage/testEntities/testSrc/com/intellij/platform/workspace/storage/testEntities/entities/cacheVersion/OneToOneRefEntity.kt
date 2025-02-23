// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Child

interface OneToOneRefEntity: WorkspaceEntity {
  val version: Int
  val text: String
  @Child val anotherEntity: com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.AnotherOneToOneRefEntity?

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<OneToOneRefEntity> {
    override var entitySource: EntitySource
    var version: Int
    var text: String
    var anotherEntity: AnotherOneToOneRefEntity.Builder?
  }

  companion object : EntityType<OneToOneRefEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      version: Int,
      text: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.version = version
      builder.text = text
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyOneToOneRefEntity(
  entity: OneToOneRefEntity,
  modification: OneToOneRefEntity.Builder.() -> Unit,
): OneToOneRefEntity {
  return modifyEntity(OneToOneRefEntity.Builder::class.java, entity, modification)
}
//endregion

interface AnotherOneToOneRefEntity: WorkspaceEntity {
  val someString: String
  val boolean: Boolean
  val parentEntity: com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.OneToOneRefEntity

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<AnotherOneToOneRefEntity> {
    override var entitySource: EntitySource
    var someString: String
    var boolean: Boolean
    var parentEntity: OneToOneRefEntity.Builder
  }

  companion object : EntityType<AnotherOneToOneRefEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      someString: String,
      boolean: Boolean,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.someString = someString
      builder.boolean = boolean
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyAnotherOneToOneRefEntity(
  entity: AnotherOneToOneRefEntity,
  modification: AnotherOneToOneRefEntity.Builder.() -> Unit,
): AnotherOneToOneRefEntity {
  return modifyEntity(AnotherOneToOneRefEntity.Builder::class.java, entity, modification)
}
//endregion
