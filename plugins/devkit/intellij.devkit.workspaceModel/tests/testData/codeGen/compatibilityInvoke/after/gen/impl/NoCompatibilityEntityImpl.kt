@file:OptIn(EntityStorageInstrumentationApi::class)
package com.intellij.workspaceModel.test.api.impl

import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.workspaceModel.test.api.NoCompatibilityEntity
import com.intellij.workspaceModel.test.api.NoCompatibilityEntityBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class NoCompatibilityEntityImpl(private val dataSource: NoCompatibilityEntityData): NoCompatibilityEntity, WorkspaceEntityBase(dataSource){

override val version: Int
get(){
readField("version")
return dataSource.version
}
override val name: String
get(){
readField("name")
return dataSource.name
}
override val isSimple: Boolean
get(){
readField("isSimple")
return dataSource.isSimple
}
override val entitySource: EntitySource
get(){
readField("entitySource")
return dataSource.entitySource
}
override fun connectionIdList(): List<ConnectionId>{
return emptyList()
}
internal class Builder(result: NoCompatibilityEntityData?): ModifiableWorkspaceEntityBase<NoCompatibilityEntity, NoCompatibilityEntityData>(result), NoCompatibilityEntityBuilder{
internal constructor(): this(NoCompatibilityEntityData())
override fun checkInitialization(){
val _diff = diff
if (!getEntityData().isEntitySourceInitialized()){
error("Field WorkspaceEntity#entitySource should be initialized")
}
if (!getEntityData().isNameInitialized()){
error("Field NoCompatibilityEntity#name should be initialized")
}
}
override fun connectionIdList(): List<ConnectionId>{
return emptyList()
}
// Relabeling code, move information from dataSource to this builder
override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?){
dataSource as NoCompatibilityEntity
if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
if (this.version != dataSource.version) this.version = dataSource.version
if (this.name != dataSource.name) this.name = dataSource.name
if (this.isSimple != dataSource.isSimple) this.isSimple = dataSource.isSimple
updateChildToParentReferences(parents)
}
override var entitySource: EntitySource
get() = getEntityData().entitySource
set(value){
checkModificationAllowed()
getEntityData(true).entitySource = value
changedProperty.add("entitySource")
}
override var version: Int
get() = getEntityData().version
set(value){
checkModificationAllowed()
getEntityData(true).version = value
changedProperty.add("version")
}
override var name: String
get() = getEntityData().name
set(value){
checkModificationAllowed()
getEntityData(true).name = value
changedProperty.add("name")
}
override var isSimple: Boolean
get() = getEntityData().isSimple
set(value){
checkModificationAllowed()
getEntityData(true).isSimple = value
changedProperty.add("isSimple")
}
override fun getEntityClass(): Class<NoCompatibilityEntity> = NoCompatibilityEntity::class.java
}
}
@OptIn(WorkspaceEntityInternalApi::class)
internal class NoCompatibilityEntityData : WorkspaceEntityData<NoCompatibilityEntity>(){
var version: Int = 0
lateinit var name: String
var isSimple: Boolean = false
internal fun isNameInitialized(): Boolean = ::name.isInitialized
override fun newInstance(): NoCompatibilityEntity = NoCompatibilityEntityImpl(this)
override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<NoCompatibilityEntity, *> = NoCompatibilityEntityImpl.Builder(null)
override fun getMetadata(): EntityMetadata{
return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.workspaceModel.test.api.NoCompatibilityEntity") as EntityMetadata
}
override fun getEntityInterface(): Class<out WorkspaceEntity>{
return NoCompatibilityEntity::class.java
}
override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*>{
return NoCompatibilityEntity(version, name, isSimple, entitySource)
}
override fun getRequiredParents(): List<Class<out WorkspaceEntity>>{
val res = mutableListOf<Class<out WorkspaceEntity>>()
return res
}
override fun equals(other: Any?): Boolean{
if (other == null) return false
if (this.javaClass != other.javaClass) return false
other as NoCompatibilityEntityData
if (this.entitySource != other.entitySource) return false
if (this.version != other.version) return false
if (this.name != other.name) return false
if (this.isSimple != other.isSimple) return false
return true
}
override fun equalsIgnoringEntitySource(other: Any?): Boolean{
if (other == null) return false
if (this.javaClass != other.javaClass) return false
other as NoCompatibilityEntityData
if (this.version != other.version) return false
if (this.name != other.name) return false
if (this.isSimple != other.isSimple) return false
return true
}
override fun hashCode(): Int{
var result = entitySource.hashCode()
result = 31 * result + version.hashCode()
result = 31 * result + name.hashCode()
result = 31 * result + isSimple.hashCode()
return result
}
override fun hashCodeIgnoringEntitySource(): Int{
var result = javaClass.hashCode()
result = 31 * result + version.hashCode()
result = 31 * result + name.hashCode()
result = 31 * result + isSimple.hashCode()
return result
}
}
