@file:OptIn(EntityStorageInstrumentationApi::class)
package com.intellij.workspaceModel.test.api.impl

import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.test.api.CustomEntity
import com.intellij.workspaceModel.test.api.CustomEntityBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class CustomEntityImpl(private val dataSource: CustomEntityData): CustomEntity, WorkspaceEntityBase(dataSource){

override val name: String
get(){
readField("name")
return dataSource.name
}
override val hasSuper: Boolean
get(){
readField("hasSuper")
return dataSource.hasSuper
}
override val hasSuperSuper: Boolean
get(){
readField("hasSuperSuper")
return dataSource.hasSuperSuper
}
override val url: VirtualFileUrl
get(){
readField("url")
return dataSource.url
}
override val entitySource: EntitySource
get(){
readField("entitySource")
return dataSource.entitySource
}
override fun connectionIdList(): List<ConnectionId>{
return emptyList()
}
internal class Builder(result: CustomEntityData?): ModifiableWorkspaceEntityBase<CustomEntity, CustomEntityData>(result), CustomEntityBuilder{
internal constructor(): this(CustomEntityData())
override fun checkInitialization(){
val _diff = diff
if (!getEntityData().isEntitySourceInitialized()){
error("Field WorkspaceEntity#entitySource should be initialized")
}
if (!getEntityData().isNameInitialized()){
error("Field CustomEntity#name should be initialized")
}
if (!getEntityData().isUrlInitialized()){
error("Field CustomEntity#url should be initialized")
}
}
override fun connectionIdList(): List<ConnectionId>{
return emptyList()
}
// Relabeling code, move information from dataSource to this builder
override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?){
dataSource as CustomEntity
if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
if (this.name != dataSource.name) this.name = dataSource.name
if (this.hasSuper != dataSource.hasSuper) this.hasSuper = dataSource.hasSuper
if (this.hasSuperSuper != dataSource.hasSuperSuper) this.hasSuperSuper = dataSource.hasSuperSuper
if (this.url != dataSource.url) this.url = dataSource.url
updateChildToParentReferences(parents)
}
override fun index(){
index(this, "url", this.url)
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
override var hasSuper: Boolean
get() = getEntityData().hasSuper
set(value){
checkModificationAllowed()
getEntityData(true).hasSuper = value
changedProperty.add("hasSuper")
}
override var hasSuperSuper: Boolean
get() = getEntityData().hasSuperSuper
set(value){
checkModificationAllowed()
getEntityData(true).hasSuperSuper = value
changedProperty.add("hasSuperSuper")
}
override var url: VirtualFileUrl
get() = getEntityData().url
set(value){
checkModificationAllowed()
getEntityData(true).url = value
changedProperty.add("url")
val _diff = diff
if (_diff != null) index(this, "url", value)
}
override fun getEntityClass(): Class<CustomEntity> = CustomEntity::class.java
}
}
@OptIn(WorkspaceEntityInternalApi::class)
internal class CustomEntityData : WorkspaceEntityData<CustomEntity>(){
lateinit var name: String
var hasSuper: Boolean = false
var hasSuperSuper: Boolean = false
lateinit var url: VirtualFileUrl
internal fun isNameInitialized(): Boolean = ::name.isInitialized
internal fun isUrlInitialized(): Boolean = ::url.isInitialized
override fun newInstance(): CustomEntity = CustomEntityImpl(this)
override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<CustomEntity, *> = CustomEntityImpl.Builder(null)
override fun getMetadata(): EntityMetadata{
return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.workspaceModel.test.api.CustomEntity") as EntityMetadata
}
override fun getEntityInterface(): Class<out WorkspaceEntity>{
return CustomEntity::class.java
}
override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*>{
return CustomEntity(name, hasSuper, hasSuperSuper, url, entitySource)
}
override fun getRequiredParents(): List<Class<out WorkspaceEntity>>{
val res = mutableListOf<Class<out WorkspaceEntity>>()
return res
}
override fun equals(other: Any?): Boolean{
if (other == null) return false
if (this.javaClass != other.javaClass) return false
other as CustomEntityData
if (this.entitySource != other.entitySource) return false
if (this.name != other.name) return false
if (this.hasSuper != other.hasSuper) return false
if (this.hasSuperSuper != other.hasSuperSuper) return false
if (this.url != other.url) return false
return true
}
override fun equalsIgnoringEntitySource(other: Any?): Boolean{
if (other == null) return false
if (this.javaClass != other.javaClass) return false
other as CustomEntityData
if (this.name != other.name) return false
if (this.hasSuper != other.hasSuper) return false
if (this.hasSuperSuper != other.hasSuperSuper) return false
if (this.url != other.url) return false
return true
}
override fun hashCode(): Int{
var result = entitySource.hashCode()
result = 31 * result + name.hashCode()
result = 31 * result + hasSuper.hashCode()
result = 31 * result + hasSuperSuper.hashCode()
result = 31 * result + url.hashCode()
return result
}
override fun hashCodeIgnoringEntitySource(): Int{
var result = javaClass.hashCode()
result = 31 * result + name.hashCode()
result = 31 * result + hasSuper.hashCode()
result = 31 * result + hasSuperSuper.hashCode()
result = 31 * result + url.hashCode()
return result
}
}
