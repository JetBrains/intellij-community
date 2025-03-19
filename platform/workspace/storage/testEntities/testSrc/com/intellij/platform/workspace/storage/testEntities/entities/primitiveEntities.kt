// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList


interface BooleanEntity : WorkspaceEntity {
  val data: Boolean

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<BooleanEntity> {
    override var entitySource: EntitySource
    var data: Boolean
  }

  companion object : EntityType<BooleanEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      data: Boolean,
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
fun MutableEntityStorage.modifyBooleanEntity(
  entity: BooleanEntity,
  modification: BooleanEntity.Builder.() -> Unit,
): BooleanEntity {
  return modifyEntity(BooleanEntity.Builder::class.java, entity, modification)
}
//endregion

interface IntEntity : WorkspaceEntity {
  val data: Int

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<IntEntity> {
    override var entitySource: EntitySource
    var data: Int
  }

  companion object : EntityType<IntEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      data: Int,
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
fun MutableEntityStorage.modifyIntEntity(
  entity: IntEntity,
  modification: IntEntity.Builder.() -> Unit,
): IntEntity {
  return modifyEntity(IntEntity.Builder::class.java, entity, modification)
}
//endregion

interface StringEntity : WorkspaceEntity {
  val data: String

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<StringEntity> {
    override var entitySource: EntitySource
    var data: String
  }

  companion object : EntityType<StringEntity, Builder>() {
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
fun MutableEntityStorage.modifyStringEntity(
  entity: StringEntity,
  modification: StringEntity.Builder.() -> Unit,
): StringEntity {
  return modifyEntity(StringEntity.Builder::class.java, entity, modification)
}
//endregion

interface ListEntity : WorkspaceEntity {
  val data: List<String>

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ListEntity> {
    override var entitySource: EntitySource
    var data: MutableList<String>
  }

  companion object : EntityType<ListEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      data: List<String>,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.data = data.toMutableWorkspaceList()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyListEntity(
  entity: ListEntity,
  modification: ListEntity.Builder.() -> Unit,
): ListEntity {
  return modifyEntity(ListEntity.Builder::class.java, entity, modification)
}
//endregion


interface OptionalIntEntity : WorkspaceEntity {
  val data: Int?

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<OptionalIntEntity> {
    override var entitySource: EntitySource
    var data: Int?
  }

  companion object : EntityType<OptionalIntEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyOptionalIntEntity(
  entity: OptionalIntEntity,
  modification: OptionalIntEntity.Builder.() -> Unit,
): OptionalIntEntity {
  return modifyEntity(OptionalIntEntity.Builder::class.java, entity, modification)
}
//endregion


interface OptionalStringEntity : WorkspaceEntity {
  val data: String?

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<OptionalStringEntity> {
    override var entitySource: EntitySource
    var data: String?
  }

  companion object : EntityType<OptionalStringEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyOptionalStringEntity(
  entity: OptionalStringEntity,
  modification: OptionalStringEntity.Builder.() -> Unit,
): OptionalStringEntity {
  return modifyEntity(OptionalStringEntity.Builder::class.java, entity, modification)
}
//endregion

// Not supported at the moment
/*
interface OptionalListIntEntity : WorkspaceEntity {
  val data: List<Int>?
}
*/
