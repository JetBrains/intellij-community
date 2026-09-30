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
import com.intellij.workspaceModel.test.api.EmptyCustomEntity
import com.intellij.workspaceModel.test.api.EmptyCustomEntityBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class EmptyCustomEntityImpl(private val dataSource: EmptyCustomEntityData): EmptyCustomEntity, WorkspaceEntityBase(dataSource){

override val something: String
get(){
readField("something")
return dataSource.something
}
override val hasSuper: Boolean
get(){
readField("hasSuper")
return dataSource.hasSuper
}
override val entitySource: EntitySource
get(){
readField("entitySource")
return dataSource.entitySource
}
override fun connectionIdList(): List<ConnectionId>{
return emptyList()
}
internal class Builder(result: EmptyCustomEntityData?): ModifiableWorkspaceEntityBase<EmptyCustomEntity, EmptyCustomEntityData>(result), EmptyCustomEntityBuilder{
internal constructor(): this(EmptyCustomEntityData())
override fun checkInitialization(){
val _diff = diff
if (!getEntityData().isEntitySourceInitialized()){
error("Field WorkspaceEntity#entitySource should be initialized")
}
if (!getEntityData().isSomethingInitialized()){
error("Field EmptyCustomEntity#something should be initialized")
}
}
override fun connectionIdList(): List<ConnectionId>{
return emptyList()
}
// Relabeling code, move information from dataSource to this builder
override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?){
dataSource as EmptyCustomEntity
if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
if (this.something != dataSource.something) this.something = dataSource.something
if (this.hasSuper != dataSource.hasSuper) this.hasSuper = dataSource.hasSuper
updateChildToParentReferences(parents)
}
override var entitySource: EntitySource
get() = getEntityData().entitySource
set(value){
checkModificationAllowed()
getEntityData(true).entitySource = value
changedProperty.add("entitySource")
}
override var something: String
get() = getEntityData().something
set(value){
checkModificationAllowed()
getEntityData(true).something = value
changedProperty.add("something")
}
override var hasSuper: Boolean
get() = getEntityData().hasSuper
set(value){
checkModificationAllowed()
getEntityData(true).hasSuper = value
changedProperty.add("hasSuper")
}
override fun getEntityClass(): Class<EmptyCustomEntity> = EmptyCustomEntity::class.java
}
}
@OptIn(WorkspaceEntityInternalApi::class)
internal class EmptyCustomEntityData : WorkspaceEntityData<EmptyCustomEntity>(){
lateinit var something: String
var hasSuper: Boolean = false
internal fun isSomethingInitialized(): Boolean = ::something.isInitialized
override fun newInstance(): EmptyCustomEntity = EmptyCustomEntityImpl(this)
override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<EmptyCustomEntity, *> = EmptyCustomEntityImpl.Builder(null)
override fun getMetadata(): EntityMetadata{
return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.workspaceModel.test.api.EmptyCustomEntity") as EntityMetadata
}
override fun getEntityInterface(): Class<out WorkspaceEntity>{
return EmptyCustomEntity::class.java
}
override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*>{
return EmptyCustomEntity(something, hasSuper, entitySource)
}
override fun getRequiredParents(): List<Class<out WorkspaceEntity>>{
val res = mutableListOf<Class<out WorkspaceEntity>>()
return res
}
override fun equals(other: Any?): Boolean{
if (other == null) return false
if (this.javaClass != other.javaClass) return false
other as EmptyCustomEntityData
if (this.entitySource != other.entitySource) return false
if (this.something != other.something) return false
if (this.hasSuper != other.hasSuper) return false
return true
}
override fun equalsIgnoringEntitySource(other: Any?): Boolean{
if (other == null) return false
if (this.javaClass != other.javaClass) return false
other as EmptyCustomEntityData
if (this.something != other.something) return false
if (this.hasSuper != other.hasSuper) return false
return true
}
override fun hashCode(): Int{
var result = entitySource.hashCode()
result = 31 * result + something.hashCode()
result = 31 * result + hasSuper.hashCode()
return result
}
override fun hashCodeIgnoringEntitySource(): Int{
var result = javaClass.hashCode()
result = 31 * result + something.hashCode()
result = 31 * result + hasSuper.hashCode()
return result
}
}
