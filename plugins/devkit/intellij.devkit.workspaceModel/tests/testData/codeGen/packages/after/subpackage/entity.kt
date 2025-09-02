package com.intellij.workspaceModel.test.api.subpackage

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity

interface SubSimpleEntity : WorkspaceEntity {
  val version: Int
  val name: String
  val isSimple: Boolean

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<SubSimpleEntity> {
    override var entitySource: EntitySource
    var version: Int
    var name: String
    var isSimple: Boolean
  }

  companion object : EntityType<SubSimpleEntity, Builder>() {
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
fun MutableEntityStorage.modifySubSimpleEntity(
  entity: SubSimpleEntity,
  modification: SubSimpleEntity.Builder.() -> Unit,
): SubSimpleEntity {
  return modifyEntity(SubSimpleEntity.Builder::class.java, entity, modification)
}
//endregion
