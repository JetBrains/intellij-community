// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Child

interface OneToManyRefEntity: WorkspaceEntity {
  val someData: com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.OneToManyRefDataClass
  val anotherEntity: List<@Child com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.AnotherOneToManyRefEntity>

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : OneToManyRefEntity, WorkspaceEntity.Builder<OneToManyRefEntity> {
    override var entitySource: EntitySource
    override var someData: OneToManyRefDataClass
    override var anotherEntity: List<AnotherOneToManyRefEntity>
  }

  companion object : EntityType<OneToManyRefEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(someData: OneToManyRefDataClass,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): OneToManyRefEntity {
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
fun MutableEntityStorage.modifyEntity(entity: OneToManyRefEntity,
                                      modification: OneToManyRefEntity.Builder.() -> Unit): OneToManyRefEntity = modifyEntity(
  OneToManyRefEntity.Builder::class.java, entity, modification)
//endregion

interface AnotherOneToManyRefEntity: WorkspaceEntity {
  val parentEntity: com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.OneToManyRefEntity
  val version: Int
  val someData: com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.OneToManyRefDataClass

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : AnotherOneToManyRefEntity, WorkspaceEntity.Builder<AnotherOneToManyRefEntity> {
    override var entitySource: EntitySource
    override var parentEntity: OneToManyRefEntity
    override var version: Int
    override var someData: OneToManyRefDataClass
  }

  companion object : EntityType<AnotherOneToManyRefEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(version: Int,
                        someData: OneToManyRefDataClass,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): AnotherOneToManyRefEntity {
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
fun MutableEntityStorage.modifyEntity(entity: AnotherOneToManyRefEntity,
                                      modification: AnotherOneToManyRefEntity.Builder.() -> Unit): AnotherOneToManyRefEntity = modifyEntity(
  AnotherOneToManyRefEntity.Builder::class.java, entity, modification)
//endregion

data class OneToManyRefDataClass(val list: List<Set<String>>, val value: Int)