@file:JvmName("EntityWithVariousVirtualFileUrlsModifications")
package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.annotations.IndexVfu
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.test.api.impl.EntityWithVariousVirtualFileUrlsImpl

@GeneratedCodeApiVersion(3)
interface EntityWithVariousVirtualFileUrlsBuilder: WorkspaceEntityBuilder<EntityWithVariousVirtualFileUrls>{
override var entitySource: EntitySource
var name: String
var version: Int
var someUrl: VirtualFileUrl
var nullableUrl: VirtualFileUrl?
var dataClassWithVfu: DataClassWithVfu
var dataClassWithDataClassWithVfu: DataClassWithDataClassWithVfu
var dataClassWithVfuCollection: DataClassWithVfuCollections
var collectionOfVfuCollections: MutableList<DataClassWithVfuCollections>
var unindexedVfu: VirtualFileUrl
var unindexedVfuCollection: MutableList<VirtualFileUrl>
var unindexedDataClassWithVfu: DataClassWithVfu
}
internal object EntityWithVariousVirtualFileUrlsType : EntityType<EntityWithVariousVirtualFileUrls, EntityWithVariousVirtualFileUrlsBuilder>(){
override val entityImplClass: Class<*> get() = EntityWithVariousVirtualFileUrlsImpl::class.java
override val entityImplBuilderClass: Class<*> get() = EntityWithVariousVirtualFileUrlsImpl.Builder::class.java
operator fun invoke(
name: String,
version: Int,
someUrl: VirtualFileUrl,
dataClassWithVfu: DataClassWithVfu,
dataClassWithDataClassWithVfu: DataClassWithDataClassWithVfu,
dataClassWithVfuCollection: DataClassWithVfuCollections,
collectionOfVfuCollections: List<DataClassWithVfuCollections>,
unindexedVfu: VirtualFileUrl,
unindexedVfuCollection: List<VirtualFileUrl>,
unindexedDataClassWithVfu: DataClassWithVfu,
entitySource: EntitySource,
init: (EntityWithVariousVirtualFileUrlsBuilder.() -> Unit)? = null,
): EntityWithVariousVirtualFileUrlsBuilder{
val builder = builder()
builder.name = name
builder.version = version
builder.someUrl = someUrl
builder.dataClassWithVfu = dataClassWithVfu
builder.dataClassWithDataClassWithVfu = dataClassWithDataClassWithVfu
builder.dataClassWithVfuCollection = dataClassWithVfuCollection
builder.collectionOfVfuCollections = collectionOfVfuCollections.toMutableWorkspaceList()
builder.unindexedVfu = unindexedVfu
builder.unindexedVfuCollection = unindexedVfuCollection.toMutableWorkspaceList()
builder.unindexedDataClassWithVfu = unindexedDataClassWithVfu
builder.entitySource = entitySource
init?.invoke(builder)
return builder
}
}
fun MutableEntityStorage.modifyEntityWithVariousVirtualFileUrls(
entity: EntityWithVariousVirtualFileUrls,
modification: EntityWithVariousVirtualFileUrlsBuilder.() -> Unit,
): EntityWithVariousVirtualFileUrls = modifyEntity(EntityWithVariousVirtualFileUrlsBuilder::class.java, entity, modification)
@JvmOverloads
@JvmName("createEntityWithVariousVirtualFileUrls")
fun EntityWithVariousVirtualFileUrls(
name: String,
version: Int,
someUrl: VirtualFileUrl,
dataClassWithVfu: DataClassWithVfu,
dataClassWithDataClassWithVfu: DataClassWithDataClassWithVfu,
dataClassWithVfuCollection: DataClassWithVfuCollections,
collectionOfVfuCollections: List<DataClassWithVfuCollections>,
unindexedVfu: VirtualFileUrl,
unindexedVfuCollection: List<VirtualFileUrl>,
unindexedDataClassWithVfu: DataClassWithVfu,
entitySource: EntitySource,
init: (EntityWithVariousVirtualFileUrlsBuilder.() -> Unit)? = null,
): EntityWithVariousVirtualFileUrlsBuilder = EntityWithVariousVirtualFileUrlsType(name, version, someUrl, dataClassWithVfu, dataClassWithDataClassWithVfu, dataClassWithVfuCollection, collectionOfVfuCollections, unindexedVfu, unindexedVfuCollection, unindexedDataClassWithVfu, entitySource, init)
