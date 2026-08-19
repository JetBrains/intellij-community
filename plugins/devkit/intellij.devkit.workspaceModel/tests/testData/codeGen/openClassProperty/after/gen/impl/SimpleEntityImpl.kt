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
import com.intellij.workspaceModel.test.api.Descriptor
import com.intellij.workspaceModel.test.api.SimpleEntity
import com.intellij.workspaceModel.test.api.SimpleEntityBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class SimpleEntityImpl(private val dataSource: SimpleEntityData): SimpleEntity, WorkspaceEntityBase(dataSource){

override val info: String
get(){
readField("info")
return dataSource.info
}
override val descriptor: Descriptor
get(){
readField("descriptor")
return dataSource.descriptor
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
if (!getEntityData().isInfoInitialized()){
error("Field SimpleEntity#info should be initialized")
}
if (!getEntityData().isDescriptorInitialized()){
error("Field SimpleEntity#descriptor should be initialized")
}
}
override fun connectionIdList(): List<ConnectionId>{
return emptyList()
}
// Relabeling code, move information from dataSource to this builder
override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?){
dataSource as SimpleEntity
if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
if (this.info != dataSource.info) this.info = dataSource.info
if (this.descriptor != dataSource.descriptor) this.descriptor = dataSource.descriptor
updateChildToParentReferences(parents)
}
override var entitySource: EntitySource
get() = getEntityData().entitySource
set(value){
checkModificationAllowed()
getEntityData(true).entitySource = value
changedProperty.add("entitySource")
}
override var info: String
get() = getEntityData().info
set(value){
checkModificationAllowed()
getEntityData(true).info = value
changedProperty.add("info")
}
override var descriptor: Descriptor
get() = getEntityData().descriptor
set(value){
checkModificationAllowed()
getEntityData(true).descriptor = value
changedProperty.add("descriptor")
}
override fun getEntityClass(): Class<SimpleEntity> = SimpleEntity::class.java
}
}
@OptIn(WorkspaceEntityInternalApi::class)
internal class SimpleEntityData : WorkspaceEntityData<SimpleEntity>(){
lateinit var info: String
lateinit var descriptor: Descriptor
internal fun isInfoInitialized(): Boolean = ::info.isInitialized
internal fun isDescriptorInitialized(): Boolean = ::descriptor.isInitialized
override fun newInstance(): SimpleEntity = SimpleEntityImpl(this)
override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<SimpleEntity, *> = SimpleEntityImpl.Builder(null)
override fun getMetadata(): EntityMetadata{
return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.workspaceModel.test.api.SimpleEntity") as EntityMetadata
}
override fun getEntityInterface(): Class<out WorkspaceEntity>{
return SimpleEntity::class.java
}
override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*>{
return SimpleEntity(info, descriptor, entitySource)
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
if (this.info != other.info) return false
if (this.descriptor != other.descriptor) return false
return true
}
override fun equalsIgnoringEntitySource(other: Any?): Boolean{
if (other == null) return false
if (this.javaClass != other.javaClass) return false
other as SimpleEntityData
if (this.info != other.info) return false
if (this.descriptor != other.descriptor) return false
return true
}
override fun hashCode(): Int{
var result = entitySource.hashCode()
result = 31 * result + info.hashCode()
result = 31 * result + descriptor.hashCode()
return result
}
override fun hashCodeIgnoringEntitySource(): Int{
var result = javaClass.hashCode()
result = 31 * result + info.hashCode()
result = 31 * result + descriptor.hashCode()
return result
}
}
