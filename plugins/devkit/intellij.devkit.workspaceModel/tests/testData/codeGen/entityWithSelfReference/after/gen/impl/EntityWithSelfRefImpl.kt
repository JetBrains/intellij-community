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
import com.intellij.workspaceModel.test.api.EntityWithSelfRef
import com.intellij.workspaceModel.test.api.EntityWithSelfRefBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class EntityWithSelfRefImpl(private val dataSource: EntityWithSelfRefData): EntityWithSelfRef, WorkspaceEntityBase(dataSource){
private companion object{
internal val PARENTREF_CONNECTION_ID: ConnectionId = ConnectionId.create(EntityWithSelfRef::class.java, EntityWithSelfRef::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, true)
internal val CHILDREN_CONNECTION_ID: ConnectionId = ConnectionId.create(EntityWithSelfRef::class.java, EntityWithSelfRef::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, true)
private val connections = listOf<ConnectionId>(PARENTREF_CONNECTION_ID,CHILDREN_CONNECTION_ID)
}

override val name: String
get(){
readField("name")
return dataSource.name
}
override val parentRef: EntityWithSelfRef?
get() = snapshot.instrumentation.getParent(PARENTREF_CONNECTION_ID, this) as? EntityWithSelfRef
override val children: List<EntityWithSelfRef>
@Suppress("UNCHECKED_CAST")
get() = (snapshot.instrumentation.getManyChildren(CHILDREN_CONNECTION_ID, this) as? Sequence<EntityWithSelfRef>)?.toList() ?: error("Children list children not found for EntityWithSelfRef")
override val entitySource: EntitySource
get(){
readField("entitySource")
return dataSource.entitySource
}
override fun connectionIdList(): List<ConnectionId>{
return connections
}
internal class Builder(result: EntityWithSelfRefData?): ModifiableWorkspaceEntityBase<EntityWithSelfRef, EntityWithSelfRefData>(result), EntityWithSelfRefBuilder{
internal constructor(): this(EntityWithSelfRefData())
override fun checkInitialization(){
val _diff = diff
if (!getEntityData().isEntitySourceInitialized()){
error("Field WorkspaceEntity#entitySource should be initialized")
}
if (!getEntityData().isNameInitialized()){
error("Field EntityWithSelfRef#name should be initialized")
}
// Check initialization for list with ref type
if (_diff != null){
if (_diff.instrumentation.getManyChildrenBuilders(CHILDREN_CONNECTION_ID, this) == null){
error("Field EntityWithSelfRef#children should be initialized")
}
}
else{
if (this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] == null){
error("Field EntityWithSelfRef#children should be initialized")
}
}
}
override fun connectionIdList(): List<ConnectionId>{
return connections
}
// Relabeling code, move information from dataSource to this builder
override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?){
dataSource as EntityWithSelfRef
if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
if (this.name != dataSource.name) this.name = dataSource.name
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
override var parentRef: EntityWithSelfRefBuilder?
get() = getParent(PARENTREF_CONNECTION_ID) as? EntityWithSelfRefBuilder? ?: error("parentRef is null for EntityWithSelfRef")
set(value){
changeParentOfMany(value, PARENTREF_CONNECTION_ID)
changedProperty.add("parentRef")
}
override var children: List<EntityWithSelfRefBuilder>
@Suppress("UNCHECKED_CAST")
get() = getChildren(CHILDREN_CONNECTION_ID) as List<EntityWithSelfRefBuilder>
set(value){
changeChildren(value, CHILDREN_CONNECTION_ID)
changedProperty.add("children")
}
override fun getEntityClass(): Class<EntityWithSelfRef> = EntityWithSelfRef::class.java
}
}
@OptIn(WorkspaceEntityInternalApi::class)
internal class EntityWithSelfRefData : WorkspaceEntityData<EntityWithSelfRef>(){
lateinit var name: String
internal fun isNameInitialized(): Boolean = ::name.isInitialized
override fun newInstance(): EntityWithSelfRef = EntityWithSelfRefImpl(this)
override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<EntityWithSelfRef, *> = EntityWithSelfRefImpl.Builder(null)
override fun getMetadata(): EntityMetadata{
return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.workspaceModel.test.api.EntityWithSelfRef") as EntityMetadata
}
override fun getEntityInterface(): Class<out WorkspaceEntity>{
return EntityWithSelfRef::class.java
}
override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*>{
return EntityWithSelfRef(name, entitySource){
this.parentRef = parents.filterIsInstance<EntityWithSelfRefBuilder>().singleOrNull()
}
}
override fun getRequiredParents(): List<Class<out WorkspaceEntity>>{
val res = mutableListOf<Class<out WorkspaceEntity>>()
return res
}
override fun equals(other: Any?): Boolean{
if (other == null) return false
if (this.javaClass != other.javaClass) return false
other as EntityWithSelfRefData
if (this.entitySource != other.entitySource) return false
if (this.name != other.name) return false
return true
}
override fun equalsIgnoringEntitySource(other: Any?): Boolean{
if (other == null) return false
if (this.javaClass != other.javaClass) return false
other as EntityWithSelfRefData
if (this.name != other.name) return false
return true
}
override fun hashCode(): Int{
var result = entitySource.hashCode()
result = 31 * result + name.hashCode()
return result
}
override fun hashCodeIgnoringEntitySource(): Int{
var result = javaClass.hashCode()
result = 31 * result + name.hashCode()
return result
}
}
