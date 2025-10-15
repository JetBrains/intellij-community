package com.intellij.platform.workspace.jps.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent

interface SimpleEntity : WorkspaceEntity {
  val version: Int
  val name: String

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<SimpleEntity> {
    override var entitySource: EntitySource
    var version: Int
    var name: String
  }

  companion object : EntityType<SimpleEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      version: Int,
      name: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.version = version
      builder.name = name
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

interface SimpleParentByExtension : WorkspaceEntity {
  val simpleName: String
  val simpleChild: SimpleEntity?

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<SimpleParentByExtension> {
    override var entitySource: EntitySource
    var simpleName: String
    var simpleChild: SimpleEntity.Builder?
  }

  companion object : EntityType<SimpleParentByExtension, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      simpleName: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.simpleName = simpleName
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifySimpleParentByExtension(
  entity: SimpleParentByExtension,
  modification: SimpleParentByExtension.Builder.() -> Unit,
): SimpleParentByExtension {
  return modifyEntity(SimpleParentByExtension.Builder::class.java, entity, modification)
}

@Parent
var SimpleEntity.Builder.simpleParent: SimpleParentByExtension.Builder
  by WorkspaceEntity.extensionBuilder(SimpleParentByExtension::class.java)
//endregion

@Parent val SimpleEntity.simpleParent: SimpleParentByExtension
  by WorkspaceEntity.extension()