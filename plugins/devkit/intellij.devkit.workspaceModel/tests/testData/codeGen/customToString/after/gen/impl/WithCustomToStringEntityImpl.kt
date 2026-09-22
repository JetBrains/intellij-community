@file:OptIn(EntityStorageInstrumentationApi::class)
package com.intellij.workspaceModel.test.api.impl

import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.annotations.ToString
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.workspaceModel.test.api.WithCustomToStringEntity
import com.intellij.workspaceModel.test.api.WithCustomToStringEntityBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class WithCustomToStringEntityImpl(private val dataSource: WithCustomToStringEntityData): WithCustomToStringEntity, WorkspaceEntityBase(dataSource){

override val myName: String
get(){
readField("myName")
return dataSource.myName
}
override val version: Int
get(){
readField("version")
return dataSource.version
}
override val entitySource: EntitySource
get(){
readField("entitySource")
return dataSource.entitySource
}
override fun connectionIdList(): List<ConnectionId>{
return emptyList()
}
override fun toString() = stringRepresentation
internal class Builder(result: WithCustomToStringEntityData?): ModifiableWorkspaceEntityBase<WithCustomToStringEntity, WithCustomToStringEntityData>(result), WithCustomToStringEntityBuilder{
internal constructor(): this(WithCustomToStringEntityData())
override fun checkInitialization(){
val _diff = diff
if (!getEntityData().isEntitySourceInitialized()){
error("Field WorkspaceEntity#entitySource should be initialized")
}
if (!getEntityData().isMyNameInitialized()){
error("Field WithCustomToStringEntity#myName should be initialized")
}
}
override fun connectionIdList(): List<ConnectionId>{
return emptyList()
}
// Relabeling code, move information from dataSource to this builder
override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?){
dataSource as WithCustomToStringEntity
if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
if (this.myName != dataSource.myName) this.myName = dataSource.myName
if (this.version != dataSource.version) this.version = dataSource.version
updateChildToParentReferences(parents)
}
override var entitySource: EntitySource
get() = getEntityData().entitySource
set(value){
checkModificationAllowed()
getEntityData(true).entitySource = value
changedProperty.add("entitySource")
}
override var myName: String
get() = getEntityData().myName
set(value){
checkModificationAllowed()
getEntityData(true).myName = value
changedProperty.add("myName")
}
override var version: Int
get() = getEntityData().version
set(value){
checkModificationAllowed()
getEntityData(true).version = value
changedProperty.add("version")
}
override fun getEntityClass(): Class<WithCustomToStringEntity> = WithCustomToStringEntity::class.java
override fun toString()="Custom entity toString: $myName & $version"
}
}
@OptIn(WorkspaceEntityInternalApi::class)
internal class WithCustomToStringEntityData : WorkspaceEntityData<WithCustomToStringEntity>(){
lateinit var myName: String
var version: Int = 0
internal fun isMyNameInitialized(): Boolean = ::myName.isInitialized
override fun newInstance(): WithCustomToStringEntity = WithCustomToStringEntityImpl(this)
override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<WithCustomToStringEntity, *> = WithCustomToStringEntityImpl.Builder(null)
override fun getMetadata(): EntityMetadata{
return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.workspaceModel.test.api.WithCustomToStringEntity") as EntityMetadata
}
override fun getEntityInterface(): Class<out WorkspaceEntity>{
return WithCustomToStringEntity::class.java
}
override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*>{
return WithCustomToStringEntity(myName, version, entitySource)
}
override fun getRequiredParents(): List<Class<out WorkspaceEntity>>{
val res = mutableListOf<Class<out WorkspaceEntity>>()
return res
}
override fun equals(other: Any?): Boolean{
if (other == null) return false
if (this.javaClass != other.javaClass) return false
other as WithCustomToStringEntityData
if (this.entitySource != other.entitySource) return false
if (this.myName != other.myName) return false
if (this.version != other.version) return false
return true
}
override fun equalsIgnoringEntitySource(other: Any?): Boolean{
if (other == null) return false
if (this.javaClass != other.javaClass) return false
other as WithCustomToStringEntityData
if (this.myName != other.myName) return false
if (this.version != other.version) return false
return true
}
override fun hashCode(): Int{
var result = entitySource.hashCode()
result = 31 * result + myName.hashCode()
result = 31 * result + version.hashCode()
return result
}
override fun hashCodeIgnoringEntitySource(): Int{
var result = javaClass.hashCode()
result = 31 * result + myName.hashCode()
result = 31 * result + version.hashCode()
return result
}
}
