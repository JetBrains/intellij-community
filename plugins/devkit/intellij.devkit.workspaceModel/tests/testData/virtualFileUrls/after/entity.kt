package com.intellij.workspaceModel.test.api

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.impl.containers.toMutableWorkspaceList
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type

interface EntityWithUrls : WorkspaceEntity {
  val simpleUrl: VirtualFileUrl
  val nullableUrl: VirtualFileUrl?
  val listOfUrls: List<VirtualFileUrl>
  val dataClassWithUrl: DataClassWithUrl

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : EntityWithUrls, WorkspaceEntity.Builder<EntityWithUrls>, ObjBuilder<EntityWithUrls> {
    override var entitySource: EntitySource
    override var simpleUrl: VirtualFileUrl
    override var nullableUrl: VirtualFileUrl?
    override var listOfUrls: MutableList<VirtualFileUrl>
    override var dataClassWithUrl: DataClassWithUrl
  }

  companion object : Type<EntityWithUrls, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(simpleUrl: VirtualFileUrl,
                        listOfUrls: List<VirtualFileUrl>,
                        dataClassWithUrl: DataClassWithUrl,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): EntityWithUrls {
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
fun MutableEntityStorage.modifyEntity(entity: EntityWithUrls, modification: EntityWithUrls.Builder.() -> Unit) = modifyEntity(
  EntityWithUrls.Builder::class.java, entity, modification)
//endregion

data class DataClassWithUrl(val url: VirtualFileUrl)