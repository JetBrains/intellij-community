@file:OptIn(EntityStorageInstrumentationApi::class)
package com.intellij.workspaceModel.test.api.impl

import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.annotations.IndexVfu
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.containers.MutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.test.api.DataClassWithDataClassWithVfu
import com.intellij.workspaceModel.test.api.DataClassWithVfu
import com.intellij.workspaceModel.test.api.DataClassWithVfuCollections
import com.intellij.workspaceModel.test.api.EntityWithVariousVirtualFileUrls
import com.intellij.workspaceModel.test.api.EntityWithVariousVirtualFileUrlsBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class EntityWithVariousVirtualFileUrlsImpl(private val dataSource: EntityWithVariousVirtualFileUrlsData): EntityWithVariousVirtualFileUrls, WorkspaceEntityBase(dataSource){

override val name: String
get(){
readField("name")
return dataSource.name
}
override val version: Int
get(){
readField("version")
return dataSource.version
}
override val someUrl: VirtualFileUrl
get(){
readField("someUrl")
return dataSource.someUrl
}
override val nullableUrl: VirtualFileUrl?
get(){
readField("nullableUrl")
return dataSource.nullableUrl
}
override val dataClassWithVfu: DataClassWithVfu
get(){
readField("dataClassWithVfu")
return dataSource.dataClassWithVfu
}
override val dataClassWithDataClassWithVfu: DataClassWithDataClassWithVfu
get(){
readField("dataClassWithDataClassWithVfu")
return dataSource.dataClassWithDataClassWithVfu
}
override val dataClassWithVfuCollection: DataClassWithVfuCollections
get(){
readField("dataClassWithVfuCollection")
return dataSource.dataClassWithVfuCollection
}
override val collectionOfVfuCollections: List<DataClassWithVfuCollections>
get(){
readField("collectionOfVfuCollections")
return dataSource.collectionOfVfuCollections
}
override val unindexedVfu: VirtualFileUrl
get(){
readField("unindexedVfu")
return dataSource.unindexedVfu
}
override val unindexedVfuCollection: List<VirtualFileUrl>
get(){
readField("unindexedVfuCollection")
return dataSource.unindexedVfuCollection
}
override val unindexedDataClassWithVfu: DataClassWithVfu
get(){
readField("unindexedDataClassWithVfu")
return dataSource.unindexedDataClassWithVfu
}
override val entitySource: EntitySource
get(){
readField("entitySource")
return dataSource.entitySource
}
override fun connectionIdList(): List<ConnectionId>{
return emptyList()
}
internal class Builder(result: EntityWithVariousVirtualFileUrlsData?): ModifiableWorkspaceEntityBase<EntityWithVariousVirtualFileUrls, EntityWithVariousVirtualFileUrlsData>(result), EntityWithVariousVirtualFileUrlsBuilder{
internal constructor(): this(EntityWithVariousVirtualFileUrlsData())
override fun checkInitialization(){
val _diff = diff
if (!getEntityData().isEntitySourceInitialized()){
error("Field WorkspaceEntity#entitySource should be initialized")
}
if (!getEntityData().isNameInitialized()){
error("Field EntityWithVariousVirtualFileUrls#name should be initialized")
}
if (!getEntityData().isSomeUrlInitialized()){
error("Field EntityWithVariousVirtualFileUrls#someUrl should be initialized")
}
if (!getEntityData().isDataClassWithVfuInitialized()){
error("Field EntityWithVariousVirtualFileUrls#dataClassWithVfu should be initialized")
}
if (!getEntityData().isDataClassWithDataClassWithVfuInitialized()){
error("Field EntityWithVariousVirtualFileUrls#dataClassWithDataClassWithVfu should be initialized")
}
if (!getEntityData().isDataClassWithVfuCollectionInitialized()){
error("Field EntityWithVariousVirtualFileUrls#dataClassWithVfuCollection should be initialized")
}
if (!getEntityData().isCollectionOfVfuCollectionsInitialized()){
error("Field EntityWithVariousVirtualFileUrls#collectionOfVfuCollections should be initialized")
}
if (!getEntityData().isUnindexedVfuInitialized()){
error("Field EntityWithVariousVirtualFileUrls#unindexedVfu should be initialized")
}
if (!getEntityData().isUnindexedVfuCollectionInitialized()){
error("Field EntityWithVariousVirtualFileUrls#unindexedVfuCollection should be initialized")
}
if (!getEntityData().isUnindexedDataClassWithVfuInitialized()){
error("Field EntityWithVariousVirtualFileUrls#unindexedDataClassWithVfu should be initialized")
}
}
override fun connectionIdList(): List<ConnectionId>{
return emptyList()
}
override fun afterModification(){
val collection_collectionOfVfuCollections = getEntityData().collectionOfVfuCollections
if (collection_collectionOfVfuCollections is MutableWorkspaceList<*>){
collection_collectionOfVfuCollections.cleanModificationUpdateAction()
}
val collection_unindexedVfuCollection = getEntityData().unindexedVfuCollection
if (collection_unindexedVfuCollection is MutableWorkspaceList<*>){
collection_unindexedVfuCollection.cleanModificationUpdateAction()
}
}
// Relabeling code, move information from dataSource to this builder
override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?){
dataSource as EntityWithVariousVirtualFileUrls
if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
if (this.name != dataSource.name) this.name = dataSource.name
if (this.version != dataSource.version) this.version = dataSource.version
if (this.someUrl != dataSource.someUrl) this.someUrl = dataSource.someUrl
if (this.nullableUrl != dataSource.nullableUrl) this.nullableUrl = dataSource.nullableUrl
if (this.dataClassWithVfu != dataSource.dataClassWithVfu) this.dataClassWithVfu = dataSource.dataClassWithVfu
if (this.dataClassWithDataClassWithVfu != dataSource.dataClassWithDataClassWithVfu) this.dataClassWithDataClassWithVfu = dataSource.dataClassWithDataClassWithVfu
if (this.dataClassWithVfuCollection != dataSource.dataClassWithVfuCollection) this.dataClassWithVfuCollection = dataSource.dataClassWithVfuCollection
if (this.collectionOfVfuCollections != dataSource.collectionOfVfuCollections) this.collectionOfVfuCollections = dataSource.collectionOfVfuCollections.toMutableList()
if (this.unindexedVfu != dataSource.unindexedVfu) this.unindexedVfu = dataSource.unindexedVfu
if (this.unindexedVfuCollection != dataSource.unindexedVfuCollection) this.unindexedVfuCollection = dataSource.unindexedVfuCollection.toMutableList()
if (this.unindexedDataClassWithVfu != dataSource.unindexedDataClassWithVfu) this.unindexedDataClassWithVfu = dataSource.unindexedDataClassWithVfu
updateChildToParentReferences(parents)
}
override fun index(){
index(this, "someUrl", this.someUrl)
index(this, "nullableUrl", this.nullableUrl)
index(this, "dataClassWithVfu.virtualFileUrl", this.dataClassWithVfu.virtualFileUrl)
index(this, "dataClassWithDataClassWithVfu.dataclassWithVfu.virtualFileUrl", this.dataClassWithDataClassWithVfu.dataclassWithVfu.virtualFileUrl)
index(this, "dataClassWithDataClassWithVfu.virtualFileUrl", this.dataClassWithDataClassWithVfu.virtualFileUrl)
index(this, "dataClassWithVfuCollection.dataClassCollection.dataclassWithVfu.virtualFileUrl", this.dataClassWithVfuCollection.dataClassCollection.map { it.dataclassWithVfu.virtualFileUrl })
index(this, "dataClassWithVfuCollection.dataClassCollection.virtualFileUrl", this.dataClassWithVfuCollection.dataClassCollection.map { it.virtualFileUrl })
index(this, "dataClassWithVfuCollection.deepVfuListCollection.deepVfuList", this.dataClassWithVfuCollection.deepVfuListCollection.flatMap { it.deepVfuList })
index(this, "dataClassWithVfuCollection.vfuCollection", this.dataClassWithVfuCollection.vfuCollection)
index(this, "collectionOfVfuCollections.dataClassCollection.dataclassWithVfu.virtualFileUrl", this.collectionOfVfuCollections.flatMap { it.dataClassCollection.map { it.dataclassWithVfu.virtualFileUrl } })
index(this, "collectionOfVfuCollections.dataClassCollection.virtualFileUrl", this.collectionOfVfuCollections.flatMap { it.dataClassCollection.map { it.virtualFileUrl } })
index(this, "collectionOfVfuCollections.deepVfuListCollection.deepVfuList", this.collectionOfVfuCollections.flatMap { it.deepVfuListCollection.flatMap { it.deepVfuList } })
index(this, "collectionOfVfuCollections.vfuCollection", this.collectionOfVfuCollections.flatMap { it.vfuCollection })
}
override var entitySource: EntitySource
get() = getEntityData().entitySource
set(value){
checkModificationAllowed()
getEntityData(true).entitySource = value
changedProperty.add("entitySource")
}
override var name: String
get() = getEntityData().name
set(value){
checkModificationAllowed()
getEntityData(true).name = value
changedProperty.add("name")
}
override var version: Int
get() = getEntityData().version
set(value){
checkModificationAllowed()
getEntityData(true).version = value
changedProperty.add("version")
}
override var someUrl: VirtualFileUrl
get() = getEntityData().someUrl
set(value){
checkModificationAllowed()
getEntityData(true).someUrl = value
changedProperty.add("someUrl")
val _diff = diff
if (_diff != null) {
index(this, "someUrl", value)
}
}
override var nullableUrl: VirtualFileUrl?
get() = getEntityData().nullableUrl
set(value){
checkModificationAllowed()
getEntityData(true).nullableUrl = value
changedProperty.add("nullableUrl")
val _diff = diff
if (_diff != null) {
index(this, "nullableUrl", value)
}
}
override var dataClassWithVfu: DataClassWithVfu
get() = getEntityData().dataClassWithVfu
set(value){
checkModificationAllowed()
getEntityData(true).dataClassWithVfu = value
changedProperty.add("dataClassWithVfu")
val _diff = diff
if (_diff != null) {
index(this, "dataClassWithVfu.virtualFileUrl", value.virtualFileUrl)
}
}
override var dataClassWithDataClassWithVfu: DataClassWithDataClassWithVfu
get() = getEntityData().dataClassWithDataClassWithVfu
set(value){
checkModificationAllowed()
getEntityData(true).dataClassWithDataClassWithVfu = value
changedProperty.add("dataClassWithDataClassWithVfu")
val _diff = diff
if (_diff != null) {
index(this, "dataClassWithDataClassWithVfu.dataclassWithVfu.virtualFileUrl", value.dataclassWithVfu.virtualFileUrl)
index(this, "dataClassWithDataClassWithVfu.virtualFileUrl", value.virtualFileUrl)
}
}
override var dataClassWithVfuCollection: DataClassWithVfuCollections
get() = getEntityData().dataClassWithVfuCollection
set(value){
checkModificationAllowed()
getEntityData(true).dataClassWithVfuCollection = value
changedProperty.add("dataClassWithVfuCollection")
val _diff = diff
if (_diff != null) {
index(this, "dataClassWithVfuCollection.dataClassCollection.dataclassWithVfu.virtualFileUrl", value.dataClassCollection.map { it.dataclassWithVfu.virtualFileUrl })
index(this, "dataClassWithVfuCollection.dataClassCollection.virtualFileUrl", value.dataClassCollection.map { it.virtualFileUrl })
index(this, "dataClassWithVfuCollection.deepVfuListCollection.deepVfuList", value.deepVfuListCollection.flatMap { it.deepVfuList })
index(this, "dataClassWithVfuCollection.vfuCollection", value.vfuCollection)
}
}
private val collectionOfVfuCollectionsUpdater: (value: List<DataClassWithVfuCollections>) -> Unit = { value ->
if (diff != null) {
index(this, "collectionOfVfuCollections.dataClassCollection.dataclassWithVfu.virtualFileUrl", value.flatMap { it.dataClassCollection.map { it.dataclassWithVfu.virtualFileUrl } })
index(this, "collectionOfVfuCollections.dataClassCollection.virtualFileUrl", value.flatMap { it.dataClassCollection.map { it.virtualFileUrl } })
index(this, "collectionOfVfuCollections.deepVfuListCollection.deepVfuList", value.flatMap { it.deepVfuListCollection.flatMap { it.deepVfuList } })
index(this, "collectionOfVfuCollections.vfuCollection", value.flatMap { it.vfuCollection })
}
changedProperty.add("collectionOfVfuCollections")
}
override var collectionOfVfuCollections: MutableList<DataClassWithVfuCollections>
get(){
val collection_collectionOfVfuCollections = getEntityData().collectionOfVfuCollections
if (collection_collectionOfVfuCollections !is MutableWorkspaceList) return collection_collectionOfVfuCollections
if (diff == null || modifiable.get()) {
collection_collectionOfVfuCollections.setModificationUpdateAction(collectionOfVfuCollectionsUpdater)
} else {
collection_collectionOfVfuCollections.cleanModificationUpdateAction()
}
return collection_collectionOfVfuCollections
}
set(value){
checkModificationAllowed()
getEntityData(true).collectionOfVfuCollections = value
collectionOfVfuCollectionsUpdater.invoke(value)
}
override var unindexedVfu: VirtualFileUrl
get() = getEntityData().unindexedVfu
set(value){
checkModificationAllowed()
getEntityData(true).unindexedVfu = value
changedProperty.add("unindexedVfu")
}
private val unindexedVfuCollectionUpdater: (value: List<VirtualFileUrl>) -> Unit = { value ->
changedProperty.add("unindexedVfuCollection")
}
override var unindexedVfuCollection: MutableList<VirtualFileUrl>
get(){
val collection_unindexedVfuCollection = getEntityData().unindexedVfuCollection
if (collection_unindexedVfuCollection !is MutableWorkspaceList) return collection_unindexedVfuCollection
if (diff == null || modifiable.get()) {
collection_unindexedVfuCollection.setModificationUpdateAction(unindexedVfuCollectionUpdater)
} else {
collection_unindexedVfuCollection.cleanModificationUpdateAction()
}
return collection_unindexedVfuCollection
}
set(value){
checkModificationAllowed()
getEntityData(true).unindexedVfuCollection = value
unindexedVfuCollectionUpdater.invoke(value)
}
override var unindexedDataClassWithVfu: DataClassWithVfu
get() = getEntityData().unindexedDataClassWithVfu
set(value){
checkModificationAllowed()
getEntityData(true).unindexedDataClassWithVfu = value
changedProperty.add("unindexedDataClassWithVfu")
}
override fun getEntityClass(): Class<EntityWithVariousVirtualFileUrls> = EntityWithVariousVirtualFileUrls::class.java
}
}
@OptIn(WorkspaceEntityInternalApi::class)
internal class EntityWithVariousVirtualFileUrlsData : WorkspaceEntityData<EntityWithVariousVirtualFileUrls>(){
lateinit var name: String
var version: Int = 0
lateinit var someUrl: VirtualFileUrl
var nullableUrl: VirtualFileUrl? = null
lateinit var dataClassWithVfu: DataClassWithVfu
lateinit var dataClassWithDataClassWithVfu: DataClassWithDataClassWithVfu
lateinit var dataClassWithVfuCollection: DataClassWithVfuCollections
lateinit var collectionOfVfuCollections: MutableList<DataClassWithVfuCollections>
lateinit var unindexedVfu: VirtualFileUrl
lateinit var unindexedVfuCollection: MutableList<VirtualFileUrl>
lateinit var unindexedDataClassWithVfu: DataClassWithVfu
internal fun isNameInitialized(): Boolean = ::name.isInitialized
internal fun isSomeUrlInitialized(): Boolean = ::someUrl.isInitialized
internal fun isDataClassWithVfuInitialized(): Boolean = ::dataClassWithVfu.isInitialized
internal fun isDataClassWithDataClassWithVfuInitialized(): Boolean = ::dataClassWithDataClassWithVfu.isInitialized
internal fun isDataClassWithVfuCollectionInitialized(): Boolean = ::dataClassWithVfuCollection.isInitialized
internal fun isCollectionOfVfuCollectionsInitialized(): Boolean = ::collectionOfVfuCollections.isInitialized
internal fun isUnindexedVfuInitialized(): Boolean = ::unindexedVfu.isInitialized
internal fun isUnindexedVfuCollectionInitialized(): Boolean = ::unindexedVfuCollection.isInitialized
internal fun isUnindexedDataClassWithVfuInitialized(): Boolean = ::unindexedDataClassWithVfu.isInitialized
override fun newInstance(): EntityWithVariousVirtualFileUrls = EntityWithVariousVirtualFileUrlsImpl(this)
override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<EntityWithVariousVirtualFileUrls, *> = EntityWithVariousVirtualFileUrlsImpl.Builder(null)
override fun getMetadata(): EntityMetadata{
return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.workspaceModel.test.api.EntityWithVariousVirtualFileUrls") as EntityMetadata
}
override fun clone(): EntityWithVariousVirtualFileUrlsData{
val clonedEntity = super.clone()
clonedEntity as EntityWithVariousVirtualFileUrlsData
clonedEntity.collectionOfVfuCollections = clonedEntity.collectionOfVfuCollections.toMutableWorkspaceList()
clonedEntity.unindexedVfuCollection = clonedEntity.unindexedVfuCollection.toMutableWorkspaceList()
return clonedEntity
}
override fun getEntityInterface(): Class<out WorkspaceEntity>{
return EntityWithVariousVirtualFileUrls::class.java
}
override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*>{
return EntityWithVariousVirtualFileUrls(name, version, someUrl, dataClassWithVfu, dataClassWithDataClassWithVfu, dataClassWithVfuCollection, collectionOfVfuCollections, unindexedVfu, unindexedVfuCollection, unindexedDataClassWithVfu, entitySource){
this.nullableUrl = this@EntityWithVariousVirtualFileUrlsData.nullableUrl
}
}
override fun getRequiredParents(): List<Class<out WorkspaceEntity>>{
val res = mutableListOf<Class<out WorkspaceEntity>>()
return res
}
override fun equals(other: Any?): Boolean{
if (other == null) return false
if (this.javaClass != other.javaClass) return false
other as EntityWithVariousVirtualFileUrlsData
if (this.entitySource != other.entitySource) return false
if (this.name != other.name) return false
if (this.version != other.version) return false
if (this.someUrl != other.someUrl) return false
if (this.nullableUrl != other.nullableUrl) return false
if (this.dataClassWithVfu != other.dataClassWithVfu) return false
if (this.dataClassWithDataClassWithVfu != other.dataClassWithDataClassWithVfu) return false
if (this.dataClassWithVfuCollection != other.dataClassWithVfuCollection) return false
if (this.collectionOfVfuCollections != other.collectionOfVfuCollections) return false
if (this.unindexedVfu != other.unindexedVfu) return false
if (this.unindexedVfuCollection != other.unindexedVfuCollection) return false
if (this.unindexedDataClassWithVfu != other.unindexedDataClassWithVfu) return false
return true
}
override fun equalsIgnoringEntitySource(other: Any?): Boolean{
if (other == null) return false
if (this.javaClass != other.javaClass) return false
other as EntityWithVariousVirtualFileUrlsData
if (this.name != other.name) return false
if (this.version != other.version) return false
if (this.someUrl != other.someUrl) return false
if (this.nullableUrl != other.nullableUrl) return false
if (this.dataClassWithVfu != other.dataClassWithVfu) return false
if (this.dataClassWithDataClassWithVfu != other.dataClassWithDataClassWithVfu) return false
if (this.dataClassWithVfuCollection != other.dataClassWithVfuCollection) return false
if (this.collectionOfVfuCollections != other.collectionOfVfuCollections) return false
if (this.unindexedVfu != other.unindexedVfu) return false
if (this.unindexedVfuCollection != other.unindexedVfuCollection) return false
if (this.unindexedDataClassWithVfu != other.unindexedDataClassWithVfu) return false
return true
}
override fun hashCode(): Int{
var result = entitySource.hashCode()
result = 31 * result + name.hashCode()
result = 31 * result + version.hashCode()
result = 31 * result + someUrl.hashCode()
result = 31 * result + nullableUrl.hashCode()
result = 31 * result + dataClassWithVfu.hashCode()
result = 31 * result + dataClassWithDataClassWithVfu.hashCode()
result = 31 * result + dataClassWithVfuCollection.hashCode()
result = 31 * result + collectionOfVfuCollections.hashCode()
result = 31 * result + unindexedVfu.hashCode()
result = 31 * result + unindexedVfuCollection.hashCode()
result = 31 * result + unindexedDataClassWithVfu.hashCode()
return result
}
override fun hashCodeIgnoringEntitySource(): Int{
var result = javaClass.hashCode()
result = 31 * result + name.hashCode()
result = 31 * result + version.hashCode()
result = 31 * result + someUrl.hashCode()
result = 31 * result + nullableUrl.hashCode()
result = 31 * result + dataClassWithVfu.hashCode()
result = 31 * result + dataClassWithDataClassWithVfu.hashCode()
result = 31 * result + dataClassWithVfuCollection.hashCode()
result = 31 * result + collectionOfVfuCollections.hashCode()
result = 31 * result + unindexedVfu.hashCode()
result = 31 * result + unindexedVfuCollection.hashCode()
result = 31 * result + unindexedDataClassWithVfu.hashCode()
return result
}
}
