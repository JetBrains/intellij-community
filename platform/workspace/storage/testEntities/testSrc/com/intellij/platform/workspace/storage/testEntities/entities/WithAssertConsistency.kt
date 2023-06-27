// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.WorkspaceEntity
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion

import com.intellij.platform.workspace.storage.MutableEntityStorage




// ------------------- Entity with consistency assertion --------------------------------

interface AssertConsistencyEntity : WorkspaceEntity {
  val passCheck: Boolean

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : AssertConsistencyEntity, WorkspaceEntity.Builder<AssertConsistencyEntity> {
    override var entitySource: EntitySource
    override var passCheck: Boolean
  }

  companion object : EntityType<AssertConsistencyEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(passCheck: Boolean, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): AssertConsistencyEntity {
      val builder = builder()
      builder.passCheck = passCheck
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: AssertConsistencyEntity,
                                      modification: AssertConsistencyEntity.Builder.() -> Unit) = modifyEntity(
  AssertConsistencyEntity.Builder::class.java, entity, modification)
//endregion

fun MutableEntityStorage.addAssertConsistencyEntity(passCheck: Boolean, source: EntitySource = MySource): AssertConsistencyEntity {
  val assertConsistencyEntity = AssertConsistencyEntity(passCheck, source)
  this.addEntity(assertConsistencyEntity)
  return assertConsistencyEntity
}
