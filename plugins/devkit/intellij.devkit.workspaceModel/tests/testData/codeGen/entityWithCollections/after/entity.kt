package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceSet
import com.intellij.platform.workspace.storage.ObjBuilder
import com.intellij.platform.workspace.storage.Type

interface CollectionFieldEntity : WorkspaceEntity {
  val versions: Set<Int>
  val names: List<String>

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : CollectionFieldEntity, WorkspaceEntity.Builder<CollectionFieldEntity>, ObjBuilder<CollectionFieldEntity> {
    override var entitySource: EntitySource
    override var versions: MutableSet<Int>
    override var names: MutableList<String>
  }

  companion object : Type<CollectionFieldEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(versions: Set<Int>,
                        names: List<String>,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): CollectionFieldEntity {
      val builder = builder()
      builder.versions = versions.toMutableWorkspaceSet()
      builder.names = names.toMutableWorkspaceList()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: CollectionFieldEntity, modification: CollectionFieldEntity.Builder.() -> Unit) = modifyEntity(
  CollectionFieldEntity.Builder::class.java, entity, modification)
//endregion
