@file:OptIn(EntityStorageInstrumentationApi::class)
package com.intellij.workspaceModel.test.api.impl

import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ContentRootEntityBuilder
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
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.instrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.workspaceModel.test.api.ReferredEntity
import com.intellij.workspaceModel.test.api.ReferredEntityBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class ReferredEntityImpl(private val dataSource: ReferredEntityData): ReferredEntity, WorkspaceEntityBase(dataSource){
private companion object{
internal val CONTENTROOT_CONNECTION_ID: ConnectionId = ConnectionId.create(ReferredEntity::class.java, ContentRootEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
private val connections = listOf<ConnectionId>(CONTENTROOT_CONNECTION_ID)
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
override val contentRoot: ContentRootEntity?
get() = snapshot.instrumentation.getOneChild(CONTENTROOT_CONNECTION_ID, this) as? ContentRootEntity
override val entitySource: EntitySource
get(){
readField("entitySource")
return dataSource.entitySource
}
override fun connectionIdList(): List<ConnectionId>{
return connections
}
internal class Builder(result: ReferredEntityData?): ModifiableWorkspaceEntityBase<ReferredEntity, ReferredEntityData>(result), ReferredEntityBuilder{
internal constructor(): this(ReferredEntityData())
override fun checkInitialization(){
val _diff = diff
if (!getEntityData().isEntitySourceInitialized()){
error("Field WorkspaceEntity#entitySource should be initialized")
}
if (!getEntityData().isNameInitialized()){
error("Field ReferredEntity#name should be initialized")
}
}
override fun connectionIdList(): List<ConnectionId>{
return connections
}
// Relabeling code, move information from dataSource to this builder
override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?){
dataSource as ReferredEntity
if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
if (this.version != dataSource.version) this.version = dataSource.version
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
override var contentRoot: ContentRootEntityBuilder?
get(){
val _diff = diff
return if (_diff != null) {
((_diff as MutableEntityStorageInstrumentation).getOneChildBuilder(CONTENTROOT_CONNECTION_ID, this) as? ContentRootEntityBuilder) ?: (this.entityLinks[EntityLink(true, CONTENTROOT_CONNECTION_ID)] as? ContentRootEntityBuilder)
} else {
(this.entityLinks[EntityLink(true, CONTENTROOT_CONNECTION_ID)] as? ContentRootEntityBuilder)
}
}
set(value){
checkModificationAllowed()
val _diff = diff
if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null){
value.entityLinks[EntityLink(false, CONTENTROOT_CONNECTION_ID)] = this
@Suppress("UNCHECKED_CAST")
_diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
}
if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)){
_diff.instrumentation.replaceChildren(CONTENTROOT_CONNECTION_ID, this, listOfNotNull(value))
}
else{
if (value is ModifiableWorkspaceEntityBase<*, *>){
value.entityLinks[EntityLink(false, CONTENTROOT_CONNECTION_ID)] = this
}
this.entityLinks[EntityLink(true, CONTENTROOT_CONNECTION_ID)] = value
}
changedProperty.add("contentRoot")
}
override fun getEntityClass(): Class<ReferredEntity> = ReferredEntity::class.java
}
}
@OptIn(WorkspaceEntityInternalApi::class)
internal class ReferredEntityData : WorkspaceEntityData<ReferredEntity>(){
var version: Int = 0
lateinit var name: String
internal fun isNameInitialized(): Boolean = ::name.isInitialized
override fun newInstance(): ReferredEntity = ReferredEntityImpl(this)
override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<ReferredEntity, *> = ReferredEntityImpl.Builder(null)
override fun getMetadata(): EntityMetadata{
return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.workspaceModel.test.api.ReferredEntity") as EntityMetadata
}
override fun getEntityInterface(): Class<out WorkspaceEntity>{
return ReferredEntity::class.java
}
override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*>{
return ReferredEntity(version, name, entitySource)
}
override fun getRequiredParents(): List<Class<out WorkspaceEntity>>{
val res = mutableListOf<Class<out WorkspaceEntity>>()
return res
}
override fun equals(other: Any?): Boolean{
if (other == null) return false
if (this.javaClass != other.javaClass) return false
other as ReferredEntityData
if (this.entitySource != other.entitySource) return false
if (this.version != other.version) return false
if (this.name != other.name) return false
return true
}
override fun equalsIgnoringEntitySource(other: Any?): Boolean{
if (other == null) return false
if (this.javaClass != other.javaClass) return false
other as ReferredEntityData
if (this.version != other.version) return false
if (this.name != other.name) return false
return true
}
override fun hashCode(): Int{
var result = entitySource.hashCode()
result = 31 * result + version.hashCode()
result = 31 * result + name.hashCode()
return result
}
override fun hashCodeIgnoringEntitySource(): Int{
var result = javaClass.hashCode()
result = 31 * result + version.hashCode()
result = 31 * result + name.hashCode()
return result
}
}
