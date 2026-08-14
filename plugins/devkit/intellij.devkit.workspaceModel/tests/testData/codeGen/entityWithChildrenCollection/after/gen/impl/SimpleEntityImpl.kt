@file:OptIn(EntityStorageInstrumentationApi::class)
package com.intellij.workspaceModel.test.api.impl

import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.instrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.workspaceModel.test.api.ChildrenCollectionFieldEntity
import com.intellij.workspaceModel.test.api.ChildrenCollectionFieldEntityBuilder
import com.intellij.workspaceModel.test.api.SimpleEntity
import com.intellij.workspaceModel.test.api.SimpleEntityBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class SimpleEntityImpl(private val dataSource: SimpleEntityData): SimpleEntity, WorkspaceEntityBase(dataSource){
private companion object{
internal val PARENT_CONNECTION_ID: ConnectionId = ConnectionId.create(ChildrenCollectionFieldEntity::class.java, SimpleEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
private val connections = listOf<ConnectionId>(PARENT_CONNECTION_ID)
}

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
override val parent: ChildrenCollectionFieldEntity
get() = snapshot.instrumentation.getParent(PARENT_CONNECTION_ID, this) as? ChildrenCollectionFieldEntity ?: error("Parent parent not found for SimpleEntity")
override val entitySource: EntitySource
get(){
readField("entitySource")
return dataSource.entitySource
}
override fun connectionIdList(): List<ConnectionId>{
return connections
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
if (_diff != null){
if (_diff.instrumentation.getParentBuilder(PARENT_CONNECTION_ID, this) == null){
error("Field SimpleEntity#parent should be initialized")
}
}
else{
if (this.entityLinks[EntityLink(false, PARENT_CONNECTION_ID)] == null){
error("Field SimpleEntity#parent should be initialized")
}
}
}
override fun connectionIdList(): List<ConnectionId>{
return connections
}
// Relabeling code, move information from dataSource to this builder
override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?){
dataSource as SimpleEntity
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
override var parent: ChildrenCollectionFieldEntityBuilder
get() = getParent(PARENT_CONNECTION_ID) as? ChildrenCollectionFieldEntityBuilder ?: error("parent is null for SimpleEntity")
set(value){
changeParentOfMany(value, PARENT_CONNECTION_ID)
changedProperty.add("parent")
}
override fun getEntityClass(): Class<SimpleEntity> = SimpleEntity::class.java
}
}
@OptIn(WorkspaceEntityInternalApi::class)
internal class SimpleEntityData : WorkspaceEntityData<SimpleEntity>(){
var version: Int = 0
lateinit var name: String
var isSimple: Boolean = false
internal fun isNameInitialized(): Boolean = ::name.isInitialized
override fun newInstance(): SimpleEntity = SimpleEntityImpl(this)
override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<SimpleEntity, *> = SimpleEntityImpl.Builder(null)
override fun getMetadata(): EntityMetadata{
return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.workspaceModel.test.api.SimpleEntity") as EntityMetadata
}
override fun getEntityInterface(): Class<out WorkspaceEntity>{
return SimpleEntity::class.java
}
override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*>{
return SimpleEntity(version, name, isSimple, entitySource){
parents.filterIsInstance<ChildrenCollectionFieldEntityBuilder>().singleOrNull()?.let { this.parent = it }
}
}
override fun getRequiredParents(): List<Class<out WorkspaceEntity>>{
val res = mutableListOf<Class<out WorkspaceEntity>>()
res.add(ChildrenCollectionFieldEntity::class.java)
return res
}
override fun equals(other: Any?): Boolean{
if (other == null) return false
if (this.javaClass != other.javaClass) return false
other as SimpleEntityData
if (this.entitySource != other.entitySource) return false
if (this.version != other.version) return false
if (this.name != other.name) return false
if (this.isSimple != other.isSimple) return false
return true
}
override fun equalsIgnoringEntitySource(other: Any?): Boolean{
if (other == null) return false
if (this.javaClass != other.javaClass) return false
other as SimpleEntityData
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
