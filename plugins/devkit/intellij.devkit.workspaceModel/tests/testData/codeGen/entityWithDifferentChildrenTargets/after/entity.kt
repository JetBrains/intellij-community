package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Child

interface EntityWithChildren : WorkspaceEntity {
  val name: String
  val propertyChild: @Child ChildEntityType1?
  @Child val typeChild: ChildEntityType2?

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<EntityWithChildren> {
    override var entitySource: EntitySource
    var name: String
    var propertyChild: ChildEntityType1.Builder?
    var typeChild: ChildEntityType2.Builder?
  }

  companion object : EntityType<EntityWithChildren, Builder>() {
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
fun MutableEntityStorage.modifyEntityWithChildren(
  entity: EntityWithChildren,
  modification: EntityWithChildren.Builder.() -> Unit,
): EntityWithChildren {
  return modifyEntity(EntityWithChildren.Builder::class.java, entity, modification)
}
//endregion

interface ChildEntityType1 : WorkspaceEntity {
  val version: Int
  val parent: EntityWithChildren

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ChildEntityType1> {
    override var entitySource: EntitySource
    var version: Int
    var parent: EntityWithChildren.Builder
  }

  companion object : EntityType<ChildEntityType1, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      version: Int,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.version = version
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyChildEntityType1(
  entity: ChildEntityType1,
  modification: ChildEntityType1.Builder.() -> Unit,
): ChildEntityType1 {
  return modifyEntity(ChildEntityType1.Builder::class.java, entity, modification)
}
//endregion

interface ChildEntityType2 : WorkspaceEntity {
  val version: Int
  val parent: EntityWithChildren

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ChildEntityType2> {
    override var entitySource: EntitySource
    var version: Int
    var parent: EntityWithChildren.Builder
  }

  companion object : EntityType<ChildEntityType2, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      version: Int,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.version = version
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyChildEntityType2(
  entity: ChildEntityType2,
  modification: ChildEntityType2.Builder.() -> Unit,
): ChildEntityType2 {
  return modifyEntity(ChildEntityType2.Builder::class.java, entity, modification)
}
//endregion
