// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.currentVersion

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Child

interface OneToManyRefEntity: WorkspaceEntity {
  val someData: com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.OneToManyRefDataClass
  @Child val anotherEntity: com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.AnotherOneToManyRefEntity? //Change is here, ONE_TO_MANY connection -> ONE_TO_ONE connection

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<OneToManyRefEntity> {
    override var entitySource: EntitySource
    var someData: OneToManyRefDataClass
    var anotherEntity: AnotherOneToManyRefEntity.Builder?
  }

  companion object : EntityType<OneToManyRefEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      someData: OneToManyRefDataClass,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.someData = someData
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyOneToManyRefEntity(
  entity: OneToManyRefEntity,
  modification: OneToManyRefEntity.Builder.() -> Unit,
): OneToManyRefEntity {
  return modifyEntity(OneToManyRefEntity.Builder::class.java, entity, modification)
}
//endregion

interface AnotherOneToManyRefEntity: WorkspaceEntity {
  val parentEntity: com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.OneToManyRefEntity
  val version: Int
  val someData: com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.OneToManyRefDataClass

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<AnotherOneToManyRefEntity> {
    override var entitySource: EntitySource
    var parentEntity: OneToManyRefEntity.Builder
    var version: Int
    var someData: OneToManyRefDataClass
  }

  companion object : EntityType<AnotherOneToManyRefEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      version: Int,
      someData: OneToManyRefDataClass,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.version = version
      builder.someData = someData
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyAnotherOneToManyRefEntity(
  entity: AnotherOneToManyRefEntity,
  modification: AnotherOneToManyRefEntity.Builder.() -> Unit,
): AnotherOneToManyRefEntity {
  return modifyEntity(AnotherOneToManyRefEntity.Builder::class.java, entity, modification)
}
//endregion

data class OneToManyRefDataClass(val list: List<Set<String>>, val value: Int)