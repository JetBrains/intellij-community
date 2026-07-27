// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(EntityStorageInstrumentationApi::class)

package com.intellij.devkit.workspaceModel.jsonDump.impl

import com.intellij.devkit.workspaceModel.jsonDump.AbstractClass
import com.intellij.devkit.workspaceModel.jsonDump.BaseTestEntity
import com.intellij.devkit.workspaceModel.jsonDump.BaseTestEntityBuilder
import com.intellij.devkit.workspaceModel.jsonDump.ChildEntity
import com.intellij.devkit.workspaceModel.jsonDump.ChildEntityBuilder
import com.intellij.devkit.workspaceModel.jsonDump.SingleChild
import com.intellij.devkit.workspaceModel.jsonDump.SingleChildBuilder
import com.intellij.devkit.workspaceModel.jsonDump.TestSymbolicId
import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.containers.MutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.MutableWorkspaceSet
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceSet
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.instrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class BaseTestEntityImpl(private val dataSource: BaseTestEntityData) : BaseTestEntity, WorkspaceEntityBase(dataSource) {

  private companion object {
    internal val CHILDREN_CONNECTION_ID: ConnectionId =
      ConnectionId.create(BaseTestEntity::class.java, ChildEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
    internal val SINGLECHILD_CONNECTION_ID: ConnectionId =
      ConnectionId.create(BaseTestEntity::class.java, SingleChild::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
    private val connections = listOf<ConnectionId>(CHILDREN_CONNECTION_ID, SINGLECHILD_CONNECTION_ID)

  }

  override val symbolicId: TestSymbolicId = super.symbolicId

  override val name: String
    get() {
      readField("name")
      return dataSource.name
    }
  override val children: List<ChildEntity>
    get() = (snapshot.instrumentation.getManyChildren(CHILDREN_CONNECTION_ID, this) as? Sequence<ChildEntity>)?.toList()
            ?: error("Children children not found for BaseTestEntity")
  override val singleChild: SingleChild?
    get() = snapshot.instrumentation.getOneChild(SINGLECHILD_CONNECTION_ID, this) as? SingleChild
  override val listOfAbstract: List<AbstractClass>
    get() {
      readField("listOfAbstract")
      return dataSource.listOfAbstract
    }
  override val stringList: List<String>
    get() {
      readField("stringList")
      return dataSource.stringList
    }
  override val stringSet: Set<String>
    get() {
      readField("stringSet")
      return dataSource.stringSet
    }

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: BaseTestEntityData?) : ModifiableWorkspaceEntityBase<BaseTestEntity, BaseTestEntityData>(result),
                                                        BaseTestEntityBuilder {
    internal constructor() : this(BaseTestEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity BaseTestEntity is already created in a different builder")
        }
      }
      this.diff = builder
      addToBuilder()
      this.id = getEntityData().createEntityId()
// After adding entity data to the builder, we need to unbind it and move the control over entity data to builder
// Builder may switch to snapshot at any moment and lock entity data to modification
      this.currentEntityData = null
// Process linked entities that are connected without a builder
      processLinkedEntities(builder)
      checkInitialization() // TODO uncomment and check failed tests
    }

    private fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isNameInitialized()) {
        error("Field BaseTestEntity#name should be initialized")
      }
// Check initialization for list with ref type
      if (_diff != null) {
        if (_diff.instrumentation.getManyChildrenBuilders(CHILDREN_CONNECTION_ID, this) == null) {
          error("Field BaseTestEntity#children should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] == null) {
          error("Field BaseTestEntity#children should be initialized")
        }
      }
      if (!getEntityData().isListOfAbstractInitialized()) {
        error("Field BaseTestEntity#listOfAbstract should be initialized")
      }
      if (!getEntityData().isStringListInitialized()) {
        error("Field BaseTestEntity#stringList should be initialized")
      }
      if (!getEntityData().isStringSetInitialized()) {
        error("Field BaseTestEntity#stringSet should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    override fun afterModification() {
      val collection_listOfAbstract = getEntityData().listOfAbstract
      if (collection_listOfAbstract is MutableWorkspaceList<*>) {
        collection_listOfAbstract.cleanModificationUpdateAction()
      }
      val collection_stringList = getEntityData().stringList
      if (collection_stringList is MutableWorkspaceList<*>) {
        collection_stringList.cleanModificationUpdateAction()
      }
      val collection_stringSet = getEntityData().stringSet
      if (collection_stringSet is MutableWorkspaceSet<*>) {
        collection_stringSet.cleanModificationUpdateAction()
      }
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as BaseTestEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.name != dataSource.name) this.name = dataSource.name
      if (this.listOfAbstract != dataSource.listOfAbstract) this.listOfAbstract = dataSource.listOfAbstract.toMutableList()
      if (this.stringList != dataSource.stringList) this.stringList = dataSource.stringList.toMutableList()
      if (this.stringSet != dataSource.stringSet) this.stringSet = dataSource.stringSet.toMutableSet()
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }
    override var name: String
      get() = getEntityData().name
      set(value) {
        checkModificationAllowed()
        getEntityData(true).name = value
        changedProperty.add("name")
      }

    // List of non-abstract referenced types
    var _children: List<ChildEntity>? = emptyList()
    override var children: List<ChildEntityBuilder>
      get() {
// Getter of the list of non-abstract referenced types
        val _diff = diff
        return if (_diff != null) {
          ((_diff as MutableEntityStorageInstrumentation).getManyChildrenBuilders(CHILDREN_CONNECTION_ID, this)!!
            .toList() as List<ChildEntityBuilder>) + (this.entityLinks[EntityLink(true,
                                                                                  CHILDREN_CONNECTION_ID)] as? List<ChildEntityBuilder>
                                                      ?: emptyList())
        }
        else {
          this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] as? List<ChildEntityBuilder> ?: emptyList()
        }
      }
      set(value) {
// Setter of the list of non-abstract referenced types
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null) {
          for (item_value in value) {
            if (item_value is ModifiableWorkspaceEntityBase<*, *> && (item_value as? ModifiableWorkspaceEntityBase<*, *>)?.diff == null) {
// Backref setup before adding to store
              if (item_value is ModifiableWorkspaceEntityBase<*, *>) {
                item_value.entityLinks[EntityLink(false, CHILDREN_CONNECTION_ID)] = this
              }
// else you're attaching a new entity to an existing entity that is not modifiable
              _diff.addEntity(item_value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
            }
          }
          _diff.instrumentation.replaceChildren(CHILDREN_CONNECTION_ID, this, value)
        }
        else {
          for (item_value in value) {
            if (item_value is ModifiableWorkspaceEntityBase<*, *>) {
              item_value.entityLinks[EntityLink(false, CHILDREN_CONNECTION_ID)] = this
            }
// else you're attaching a new entity to an existing entity that is not modifiable
          }
          this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] = value
        }
        changedProperty.add("children")
      }

    override var singleChild: SingleChildBuilder?
      get() {
        val _diff = diff
        return if (_diff != null) {
          ((_diff as MutableEntityStorageInstrumentation).getOneChildBuilder(SINGLECHILD_CONNECTION_ID, this) as? SingleChildBuilder)
          ?: (this.entityLinks[EntityLink(true, SINGLECHILD_CONNECTION_ID)] as? SingleChildBuilder)
        }
        else {
          (this.entityLinks[EntityLink(true, SINGLECHILD_CONNECTION_ID)] as? SingleChildBuilder)
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(false, SINGLECHILD_CONNECTION_ID)] = this
          }
// else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.instrumentation.replaceChildren(SINGLECHILD_CONNECTION_ID, this, listOfNotNull(value))
        }
        else {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(false, SINGLECHILD_CONNECTION_ID)] = this
          }
// else you're attaching a new entity to an existing entity that is not modifiable
          this.entityLinks[EntityLink(true, SINGLECHILD_CONNECTION_ID)] = value
        }
        changedProperty.add("singleChild")
      }

    private val listOfAbstractUpdater: (value: List<AbstractClass>) -> Unit = { value ->

      changedProperty.add("listOfAbstract")
    }
    override var listOfAbstract: MutableList<AbstractClass>
      get() {
        val collection_listOfAbstract = getEntityData().listOfAbstract
        if (collection_listOfAbstract !is MutableWorkspaceList) return collection_listOfAbstract
        if (diff == null || modifiable.get()) {
          collection_listOfAbstract.setModificationUpdateAction(listOfAbstractUpdater)
        }
        else {
          collection_listOfAbstract.cleanModificationUpdateAction()
        }
        return collection_listOfAbstract
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).listOfAbstract = value
        listOfAbstractUpdater.invoke(value)
      }
    private val stringListUpdater: (value: List<String>) -> Unit = { value ->

      changedProperty.add("stringList")
    }
    override var stringList: MutableList<String>
      get() {
        val collection_stringList = getEntityData().stringList
        if (collection_stringList !is MutableWorkspaceList) return collection_stringList
        if (diff == null || modifiable.get()) {
          collection_stringList.setModificationUpdateAction(stringListUpdater)
        }
        else {
          collection_stringList.cleanModificationUpdateAction()
        }
        return collection_stringList
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).stringList = value
        stringListUpdater.invoke(value)
      }
    private val stringSetUpdater: (value: Set<String>) -> Unit = { value ->

      changedProperty.add("stringSet")
    }
    override var stringSet: MutableSet<String>
      get() {
        val collection_stringSet = getEntityData().stringSet
        if (collection_stringSet !is MutableWorkspaceSet) return collection_stringSet
        if (diff == null || modifiable.get()) {
          collection_stringSet.setModificationUpdateAction(stringSetUpdater)
        }
        else {
          collection_stringSet.cleanModificationUpdateAction()
        }
        return collection_stringSet
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).stringSet = value
        stringSetUpdater.invoke(value)
      }

    override fun getEntityClass(): Class<BaseTestEntity> = BaseTestEntity::class.java
  }

}

@OptIn(WorkspaceEntityInternalApi::class)
internal class BaseTestEntityData : WorkspaceEntityData<BaseTestEntity>() {
  lateinit var name: String
  lateinit var listOfAbstract: MutableList<AbstractClass>
  lateinit var stringList: MutableList<String>
  lateinit var stringSet: MutableSet<String>

  internal fun isNameInitialized(): Boolean = ::name.isInitialized
  internal fun isListOfAbstractInitialized(): Boolean = ::listOfAbstract.isInitialized
  internal fun isStringListInitialized(): Boolean = ::stringList.isInitialized
  internal fun isStringSetInitialized(): Boolean = ::stringSet.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntityBuilder<BaseTestEntity> {
    val modifiable = BaseTestEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorageInstrumentation): BaseTestEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = BaseTestEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.devkit.workspaceModel.jsonDump.BaseTestEntity") as EntityMetadata
  }

  override fun clone(): BaseTestEntityData {
    val clonedEntity = super.clone()
    clonedEntity as BaseTestEntityData
    clonedEntity.listOfAbstract = clonedEntity.listOfAbstract.toMutableWorkspaceList()
    clonedEntity.stringList = clonedEntity.stringList.toMutableWorkspaceList()
    clonedEntity.stringSet = clonedEntity.stringSet.toMutableWorkspaceSet()
    return clonedEntity
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return BaseTestEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return BaseTestEntity(name, listOfAbstract, stringList, stringSet, entitySource)
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as BaseTestEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.name != other.name) return false
    if (this.listOfAbstract != other.listOfAbstract) return false
    if (this.stringList != other.stringList) return false
    if (this.stringSet != other.stringSet) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as BaseTestEntityData
    if (this.name != other.name) return false
    if (this.listOfAbstract != other.listOfAbstract) return false
    if (this.stringList != other.stringList) return false
    if (this.stringSet != other.stringSet) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + listOfAbstract.hashCode()
    result = 31 * result + stringList.hashCode()
    result = 31 * result + stringSet.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + listOfAbstract.hashCode()
    result = 31 * result + stringList.hashCode()
    result = 31 * result + stringSet.hashCode()
    return result
  }
}
