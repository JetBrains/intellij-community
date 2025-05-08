package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity

/**
 * com.intellij.platform.workspace.storage.testEntities.entities.ProjectModelTestEntity
 */
interface SimpleEntity : WorkspaceEntity {
  val info: String
  val descriptor: Descriptor

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<SimpleEntity> {
    override var entitySource: EntitySource
    var info: String
    var descriptor: Descriptor
  }

  companion object : EntityType<SimpleEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      info: String,
      descriptor: Descriptor,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.info = info
      builder.descriptor = descriptor
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifySimpleEntity(
  entity: SimpleEntity,
  modification: SimpleEntity.Builder.() -> Unit,
): SimpleEntity {
  return modifyEntity(SimpleEntity.Builder::class.java, entity, modification)
}
//endregion

open class Descriptor(val data: String) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Descriptor

    return data == other.data
  }

  override fun hashCode(): Int {
    return data.hashCode()
  }
}

class DescriptorInstance(data: String) : Descriptor(data)