@file:OptIn(EntityStorageInstrumentationApi::class)
package com.intellij.workspaceModel.test.api.impl

import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.SoftLinkable
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.indices.WorkspaceMutableIndex
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.workspaceModel.test.api.SimpleEntity
import com.intellij.workspaceModel.test.api.SimpleEntityBuilder
import com.intellij.workspaceModel.test.api.SimpleSymbolicId

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class SimpleEntityImpl(private val dataSource: SimpleEntityData): SimpleEntity, WorkspaceEntityBase(dataSource){
override val symbolicId: SimpleSymbolicId = super.symbolicId

override val name: String
get(){
readField("name")
return dataSource.name
}
override val relatedId: SimpleSymbolicId?
get(){
readField("relatedId")
return dataSource.relatedId
}
override val entitySource: EntitySource
get(){
readField("entitySource")
return dataSource.entitySource
}
override fun connectionIdList(): List<ConnectionId>{
return emptyList()
}
internal class Builder(result: SimpleEntityData?): ModifiableWorkspaceEntityBase<SimpleEntity, SimpleEntityData>(result), SimpleEntityBuilder{
internal constructor(): this(SimpleEntityData())
override fun checkInitialization(){
val _diff = diff
if (!getEntityData().isEntitySourceInitialized()){
error("Field WorkspaceEntity#entitySource should be initialized")
}
if (!getEntityData().isNameInitialized()){
error("Field SimpleEntity#name should be initialized")
}
}
override fun connectionIdList(): List<ConnectionId>{
return emptyList()
}
// Relabeling code, move information from dataSource to this builder
override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?){
dataSource as SimpleEntity
if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
if (this.name != dataSource.name) this.name = dataSource.name
if (this.relatedId != dataSource.relatedId) this.relatedId = dataSource.relatedId
updateChildToParentReferences(parents)
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
override var relatedId: SimpleSymbolicId?
get() = getEntityData().relatedId
set(value){
checkModificationAllowed()
getEntityData(true).relatedId = value
changedProperty.add("relatedId")
}
override fun getEntityClass(): Class<SimpleEntity> = SimpleEntity::class.java
}
}
@OptIn(WorkspaceEntityInternalApi::class)
internal class SimpleEntityData : WorkspaceEntityData<SimpleEntity>(), SoftLinkable{
lateinit var name: String
var relatedId: SimpleSymbolicId? = null
internal fun isNameInitialized(): Boolean = ::name.isInitialized
override fun getLinks(): Set<SymbolicEntityId<*>>{
val result = HashSet<SymbolicEntityId<*>>()
val optionalLink_relatedId = relatedId
if (optionalLink_relatedId != null){
result.add(optionalLink_relatedId)
}
return result
}
override fun index(index: WorkspaceMutableIndex<SymbolicEntityId<*>>){
val optionalLink_relatedId = relatedId
if (optionalLink_relatedId != null){
index.index(this, optionalLink_relatedId)
}
}
override fun updateLinksIndex(prev: Set<SymbolicEntityId<*>>, index: WorkspaceMutableIndex<SymbolicEntityId<*>>){
val mutablePreviousSet = HashSet(prev)
val optionalLink_relatedId = relatedId
if (optionalLink_relatedId != null){
val removedItem_optionalLink_relatedId = mutablePreviousSet.remove(optionalLink_relatedId)
if (!removedItem_optionalLink_relatedId){
index.index(this, optionalLink_relatedId)
}
}
for (removed in mutablePreviousSet){
index.remove(this, removed)
}
}
override fun updateLink(oldLink: SymbolicEntityId<*>, newLink: SymbolicEntityId<*>): Boolean{
var changed = false
var relatedId_data_optional = if (relatedId != null){
val relatedId___data = if (relatedId!! == oldLink){
changed = true
newLink as SimpleSymbolicId
}
else{
null
}
relatedId___data
}
else{
null
}

if (relatedId_data_optional != null){
relatedId = relatedId_data_optional
}
return changed
}
override fun newInstance(): SimpleEntity = SimpleEntityImpl(this)
override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<SimpleEntity, *> = SimpleEntityImpl.Builder(null)
override fun getMetadata(): EntityMetadata{
return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.workspaceModel.test.api.SimpleEntity") as EntityMetadata
}
override fun getEntityInterface(): Class<out WorkspaceEntity>{
return SimpleEntity::class.java
}
override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*>{
return SimpleEntity(name, entitySource){
this.relatedId = this@SimpleEntityData.relatedId
}
}
override fun getRequiredParents(): List<Class<out WorkspaceEntity>>{
val res = mutableListOf<Class<out WorkspaceEntity>>()
return res
}
override fun equals(other: Any?): Boolean{
if (other == null) return false
if (this.javaClass != other.javaClass) return false
other as SimpleEntityData
if (this.entitySource != other.entitySource) return false
if (this.name != other.name) return false
if (this.relatedId != other.relatedId) return false
return true
}
override fun equalsIgnoringEntitySource(other: Any?): Boolean{
if (other == null) return false
if (this.javaClass != other.javaClass) return false
other as SimpleEntityData
if (this.name != other.name) return false
if (this.relatedId != other.relatedId) return false
return true
}
override fun hashCode(): Int{
var result = entitySource.hashCode()
result = 31 * result + name.hashCode()
result = 31 * result + relatedId.hashCode()
return result
}
override fun hashCodeIgnoringEntitySource(): Int{
var result = javaClass.hashCode()
result = 31 * result + name.hashCode()
result = 31 * result + relatedId.hashCode()
return result
}
}
