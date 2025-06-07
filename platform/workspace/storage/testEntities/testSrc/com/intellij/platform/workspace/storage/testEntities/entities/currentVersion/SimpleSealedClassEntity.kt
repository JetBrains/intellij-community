// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.currentVersion

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Open

interface SimpleSealedClassEntity: WorkspaceEntity {
  val text: String
  val someData: com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SimpleSealedClass

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<SimpleSealedClassEntity> {
    override var entitySource: EntitySource
    var text: String
    var someData: SimpleSealedClass
  }

  companion object : EntityType<SimpleSealedClassEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      text: String,
      someData: SimpleSealedClass,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.text = text
      builder.someData = someData
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifySimpleSealedClassEntity(
  entity: SimpleSealedClassEntity,
  modification: SimpleSealedClassEntity.Builder.() -> Unit,
): SimpleSealedClassEntity {
  return modifyEntity(SimpleSealedClassEntity.Builder::class.java, entity, modification)
}
//endregion

@Open
sealed class SimpleSealedClass {
  abstract val type: String

  data class FirstKeyPropDataClass(val text: String): SimpleSealedClass() {
    override val type: String
      get() = "first"
  }

  data class SecondKeyPropDataClass(val value: Int, val list: List<String>): SimpleSealedClass() { // Change is here, new property
    override val type: String
      get() = "second"
  }
}