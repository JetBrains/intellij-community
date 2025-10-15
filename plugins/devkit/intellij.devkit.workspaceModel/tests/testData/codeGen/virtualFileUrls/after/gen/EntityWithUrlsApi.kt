package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
interface ModifiableEntityWithUrls : ModifiableWorkspaceEntity<EntityWithUrls> {
  override var entitySource: EntitySource
  var simpleUrl: VirtualFileUrl
  var nullableUrl: VirtualFileUrl?
  var listOfUrls: MutableList<VirtualFileUrl>
  var dataClassWithUrl: DataClassWithUrl
}

internal object EntityWithUrlsType : EntityType<EntityWithUrls, ModifiableEntityWithUrls>() {
  override val entityClass: Class<EntityWithUrls> get() = EntityWithUrls::class.java
  operator fun invoke(
    simpleUrl: VirtualFileUrl,
    listOfUrls: List<VirtualFileUrl>,
    dataClassWithUrl: DataClassWithUrl,
    entitySource: EntitySource,
    init: (ModifiableEntityWithUrls.() -> Unit)? = null,
  ): ModifiableEntityWithUrls {
    val builder = builder()
    builder.simpleUrl = simpleUrl
    builder.listOfUrls = listOfUrls.toMutableWorkspaceList()
    builder.dataClassWithUrl = dataClassWithUrl
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyEntityWithUrls(
  entity: EntityWithUrls,
  modification: ModifiableEntityWithUrls.() -> Unit,
): EntityWithUrls = modifyEntity(ModifiableEntityWithUrls::class.java, entity, modification)

@JvmOverloads
@JvmName("createEntityWithUrls")
fun EntityWithUrls(
  simpleUrl: VirtualFileUrl,
  listOfUrls: List<VirtualFileUrl>,
  dataClassWithUrl: DataClassWithUrl,
  entitySource: EntitySource,
  init: (ModifiableEntityWithUrls.() -> Unit)? = null,
): ModifiableEntityWithUrls = EntityWithUrlsType(simpleUrl, listOfUrls, dataClassWithUrl, entitySource, init)
