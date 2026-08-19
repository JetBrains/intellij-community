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
import com.intellij.workspaceModel.test.api.ChildEntityType1
import com.intellij.workspaceModel.test.api.ChildEntityType1Builder
import com.intellij.workspaceModel.test.api.EntityWithChildren
import com.intellij.workspaceModel.test.api.EntityWithChildrenBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class ChildEntityType1Impl(private val dataSource: ChildEntityType1Data): ChildEntityType1, WorkspaceEntityBase(dataSource){
private companion object{
internal val PARENT_CONNECTION_ID: ConnectionId = ConnectionId.create(EntityWithChildren::class.java, ChildEntityType1::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
private val connections = listOf<ConnectionId>(PARENT_CONNECTION_ID)
}

override val version: Int
get(){
readField("version")
return dataSource.version
}
override val parent: EntityWithChildren
get() = snapshot.instrumentation.getParent(PARENT_CONNECTION_ID, this) as? EntityWithChildren ?: error("Parent parent not found for ChildEntityType1")
override val entitySource: EntitySource
get(){
readField("entitySource")
return dataSource.entitySource
}
override fun connectionIdList(): List<ConnectionId>{
return connections
}
internal class Builder(result: ChildEntityType1Data?): ModifiableWorkspaceEntityBase<ChildEntityType1, ChildEntityType1Data>(result), ChildEntityType1Builder{
internal constructor(): this(ChildEntityType1Data())
override fun checkInitialization(){
val _diff = diff
if (!getEntityData().isEntitySourceInitialized()){
error("Field WorkspaceEntity#entitySource should be initialized")
}
if (_diff != null){
if (_diff.instrumentation.getParentBuilder(PARENT_CONNECTION_ID, this) == null){
error("Field ChildEntityType1#parent should be initialized")
}
}
else{
if (this.entityLinks[EntityLink(false, PARENT_CONNECTION_ID)] == null){
error("Field ChildEntityType1#parent should be initialized")
}
}
}
override fun connectionIdList(): List<ConnectionId>{
return connections
}
// Relabeling code, move information from dataSource to this builder
override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?){
dataSource as ChildEntityType1
if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
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
override var version: Int
get() = getEntityData().version
set(value){
checkModificationAllowed()
getEntityData(true).version = value
changedProperty.add("version")
}
override var parent: EntityWithChildrenBuilder
get() = getParent(PARENT_CONNECTION_ID) as? EntityWithChildrenBuilder ?: error("parent is null for ChildEntityType1")
set(value){
changeParent(value, PARENT_CONNECTION_ID)
changedProperty.add("parent")
}
override fun getEntityClass(): Class<ChildEntityType1> = ChildEntityType1::class.java
}
}
@OptIn(WorkspaceEntityInternalApi::class)
internal class ChildEntityType1Data : WorkspaceEntityData<ChildEntityType1>(){
var version: Int = 0
override fun newInstance(): ChildEntityType1 = ChildEntityType1Impl(this)
override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<ChildEntityType1, *> = ChildEntityType1Impl.Builder(null)
override fun getMetadata(): EntityMetadata{
return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.workspaceModel.test.api.ChildEntityType1") as EntityMetadata
}
override fun getEntityInterface(): Class<out WorkspaceEntity>{
return ChildEntityType1::class.java
}
override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*>{
return ChildEntityType1(version, entitySource){
parents.filterIsInstance<EntityWithChildrenBuilder>().singleOrNull()?.let { this.parent = it }
}
}
override fun getRequiredParents(): List<Class<out WorkspaceEntity>>{
val res = mutableListOf<Class<out WorkspaceEntity>>()
res.add(EntityWithChildren::class.java)
return res
}
override fun equals(other: Any?): Boolean{
if (other == null) return false
if (this.javaClass != other.javaClass) return false
other as ChildEntityType1Data
if (this.entitySource != other.entitySource) return false
if (this.version != other.version) return false
return true
}
override fun equalsIgnoringEntitySource(other: Any?): Boolean{
if (other == null) return false
if (this.javaClass != other.javaClass) return false
other as ChildEntityType1Data
if (this.version != other.version) return false
return true
}
override fun hashCode(): Int{
var result = entitySource.hashCode()
result = 31 * result + version.hashCode()
return result
}
override fun hashCodeIgnoringEntitySource(): Int{
var result = javaClass.hashCode()
result = 31 * result + version.hashCode()
return result
}
}
