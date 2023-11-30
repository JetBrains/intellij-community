// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  @GeneratedCodeApiVersion(2)
  interface Builder : OneToOneRefEntity, WorkspaceEntity.Builder<OneToOneRefEntity> {
    override var entitySource: EntitySource
    override var version: Int
    override var text: String
    override var anotherEntity: AnotherOneToOneRefEntity?
  }

  companion object : EntityType<OneToOneRefEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(version: Int, text: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): OneToOneRefEntity {
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
fun MutableEntityStorage.modifyEntity(entity: OneToOneRefEntity,
                                      modification: OneToOneRefEntity.Builder.() -> Unit): OneToOneRefEntity = modifyEntity(
  OneToOneRefEntity.Builder::class.java, entity, modification)
//endregion

interface AnotherOneToOneRefEntity: WorkspaceEntity {
  val someString: String
  val boolean: Boolean
  val parentEntity: com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.OneToOneRefEntity

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : AnotherOneToOneRefEntity, WorkspaceEntity.Builder<AnotherOneToOneRefEntity> {
    override var entitySource: EntitySource
    override var someString: String
    override var boolean: Boolean
    override var parentEntity: OneToOneRefEntity
  }

  companion object : EntityType<AnotherOneToOneRefEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(someString: String,
                        boolean: Boolean,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): AnotherOneToOneRefEntity {
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
fun MutableEntityStorage.modifyEntity(entity: AnotherOneToOneRefEntity,
                                      modification: AnotherOneToOneRefEntity.Builder.() -> Unit): AnotherOneToOneRefEntity = modifyEntity(
  AnotherOneToOneRefEntity.Builder::class.java, entity, modification)
//endregion
