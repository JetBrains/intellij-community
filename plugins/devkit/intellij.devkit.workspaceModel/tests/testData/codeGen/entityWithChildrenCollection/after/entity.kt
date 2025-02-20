package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Child

interface ChildrenCollectionFieldEntity : WorkspaceEntity {
  val name: String
  val childrenEntitiesCollection: List<@Child SimpleEntity>

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ChildrenCollectionFieldEntity> {
    override var entitySource: EntitySource
    var name: String
    var childrenEntitiesCollection: List<SimpleEntity.Builder>
  }

  companion object : EntityType<ChildrenCollectionFieldEntity, Builder>() {
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
fun MutableEntityStorage.modifyChildrenCollectionFieldEntity(
  entity: ChildrenCollectionFieldEntity,
  modification: ChildrenCollectionFieldEntity.Builder.() -> Unit,
): ChildrenCollectionFieldEntity {
  return modifyEntity(ChildrenCollectionFieldEntity.Builder::class.java, entity, modification)
}
//endregion

interface SimpleEntity : WorkspaceEntity {
  val version: Int
  val name: String
  val isSimple: Boolean
  val parent: ChildrenCollectionFieldEntity

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<SimpleEntity> {
    override var entitySource: EntitySource
    var version: Int
    var name: String
    var isSimple: Boolean
    var parent: ChildrenCollectionFieldEntity.Builder
  }

  companion object : EntityType<SimpleEntity, Builder>() {
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
fun MutableEntityStorage.modifySimpleEntity(
  entity: SimpleEntity,
  modification: SimpleEntity.Builder.() -> Unit,
): SimpleEntity {
  return modifyEntity(SimpleEntity.Builder::class.java, entity, modification)
}
//endregion
