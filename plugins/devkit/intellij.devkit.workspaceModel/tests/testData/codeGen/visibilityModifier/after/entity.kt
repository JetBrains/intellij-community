package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity

internal interface InternalEntity : WorkspaceEntity {
  val version: Int
  val name: String
  val isSimple: Boolean

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<InternalEntity> {
    override var entitySource: EntitySource
    var version: Int
    var name: String
    var isSimple: Boolean
  }

  companion object : EntityType<InternalEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      version: Int,
      name: String,
      isSimple: Boolean,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.version = version
      builder.name = name
      builder.isSimple = isSimple
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
internal fun MutableEntityStorage.modifyInternalEntity(
  entity: InternalEntity,
  modification: InternalEntity.Builder.() -> Unit,
): InternalEntity = modifyEntity(InternalEntity.Builder::class.java, entity, modification)
//endregion

private interface PrivateEntity : WorkspaceEntity {
  val name: String

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<PrivateEntity> {
    override var entitySource: EntitySource
    var name: String
  }

  companion object : EntityType<PrivateEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      name: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.name = name
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
private fun MutableEntityStorage.modifyPrivateEntity(
  entity: PrivateEntity,
  modification: PrivateEntity.Builder.() -> Unit,
): PrivateEntity = modifyEntity(PrivateEntity.Builder::class.java, entity, modification)
//endregion
