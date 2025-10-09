package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

interface EntityWithUrls : WorkspaceEntity {
  val simpleUrl: VirtualFileUrl
  val nullableUrl: VirtualFileUrl?
  val listOfUrls: List<VirtualFileUrl>
  val dataClassWithUrl: DataClassWithUrl

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<EntityWithUrls> {
    override var entitySource: EntitySource
    var simpleUrl: VirtualFileUrl
    var nullableUrl: VirtualFileUrl?
    var listOfUrls: MutableList<VirtualFileUrl>
    var dataClassWithUrl: DataClassWithUrl
  }

  companion object : EntityType<EntityWithUrls, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      simpleUrl: VirtualFileUrl,
      listOfUrls: List<VirtualFileUrl>,
      dataClassWithUrl: DataClassWithUrl,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.simpleUrl = simpleUrl
      builder.listOfUrls = listOfUrls.toMutableWorkspaceList()
      builder.dataClassWithUrl = dataClassWithUrl
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyEntityWithUrls(
  entity: EntityWithUrls,
  modification: EntityWithUrls.Builder.() -> Unit,
): EntityWithUrls = modifyEntity(EntityWithUrls.Builder::class.java, entity, modification)
//endregion

data class DataClassWithUrl(val url: VirtualFileUrl)