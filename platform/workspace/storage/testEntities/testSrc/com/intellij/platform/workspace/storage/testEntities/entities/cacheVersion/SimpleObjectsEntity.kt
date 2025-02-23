// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Open

interface SimpleObjectsEntity: WorkspaceEntity {
  val someData: com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SimpleObjectsSealedClass

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<SimpleObjectsEntity> {
    override var entitySource: EntitySource
    var someData: SimpleObjectsSealedClass
  }

  companion object : EntityType<SimpleObjectsEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      someData: SimpleObjectsSealedClass,
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
fun MutableEntityStorage.modifySimpleObjectsEntity(
  entity: SimpleObjectsEntity,
  modification: SimpleObjectsEntity.Builder.() -> Unit,
): SimpleObjectsEntity {
  return modifyEntity(SimpleObjectsEntity.Builder::class.java, entity, modification)
}
//endregion

@Open
sealed class SimpleObjectsSealedClass {
  abstract val id: Int
  abstract val data: String

  object FirstSimpleObjectsSealedClassObject: SimpleObjectsSealedClass() {
    val value: Int = 5

    override val id: Int
      get() = 1
    override val data: String
      get() = "$value"
  }

  object SecondSimpleObjectsSealedClassObject: SimpleObjectsSealedClass() {
    val list: List<String> = listOf("some text", "something", "some data")

    override val id: Int
      get() = 2
    override val data: String
      get() = "$list"
  }
}