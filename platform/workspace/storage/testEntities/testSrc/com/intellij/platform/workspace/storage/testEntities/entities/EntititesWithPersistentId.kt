// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*


interface FirstEntityWithPId : WorkspaceEntityWithSymbolicId {
  val data: String
  override val symbolicId: FirstPId
    get() {
      return FirstPId(data)
    }

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<FirstEntityWithPId> {
    override var entitySource: EntitySource
    var data: String
  }

  companion object : EntityType<FirstEntityWithPId, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      data: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.data = data
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyFirstEntityWithPId(
  entity: FirstEntityWithPId,
  modification: FirstEntityWithPId.Builder.() -> Unit,
): FirstEntityWithPId {
  return modifyEntity(FirstEntityWithPId.Builder::class.java, entity, modification)
}
//endregion

data class FirstPId(override val presentableName: String) : SymbolicEntityId<FirstEntityWithPId>

interface SecondEntityWithPId : WorkspaceEntityWithSymbolicId {
  val data: String
  override val symbolicId: SecondPId
    get() = SecondPId(data)

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<SecondEntityWithPId> {
    override var entitySource: EntitySource
    var data: String
  }

  companion object : EntityType<SecondEntityWithPId, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      data: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.data = data
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifySecondEntityWithPId(
  entity: SecondEntityWithPId,
  modification: SecondEntityWithPId.Builder.() -> Unit,
): SecondEntityWithPId {
  return modifyEntity(SecondEntityWithPId.Builder::class.java, entity, modification)
}
//endregion

data class SecondPId(override val presentableName: String) : SymbolicEntityId<SecondEntityWithPId>
data class TestPId(var presentableName: String, val aaa: Int?, var angry: Boolean)