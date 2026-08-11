// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(EntityStorageInstrumentationApi::class)

package com.intellij.platform.workspace.storage.testEntities.entities.impl

import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.instrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.testEntities.entities.AttachedEntityToNullableParent
import com.intellij.platform.workspace.storage.testEntities.entities.AttachedEntityToNullableParentBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.AttachedEntityToParent
import com.intellij.platform.workspace.storage.testEntities.entities.AttachedEntityToParentBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.MainEntityToParent
import com.intellij.platform.workspace.storage.testEntities.entities.MainEntityToParentBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class MainEntityToParentImpl(private val dataSource: MainEntityToParentData) : MainEntityToParent,
                                                                                        WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val CHILD_CONNECTION_ID: ConnectionId =
      ConnectionId.create(MainEntityToParent::class.java, AttachedEntityToParent::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
    internal val CHILDNULLABLEPARENT_CONNECTION_ID: ConnectionId = ConnectionId.create(MainEntityToParent::class.java,
                                                                                       AttachedEntityToNullableParent::class.java,
                                                                                       ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                       true)
    private val connections = listOf<ConnectionId>(CHILD_CONNECTION_ID, CHILDNULLABLEPARENT_CONNECTION_ID)
  }

  override val x: String
    get() {
      readField("x")
      return dataSource.x
    }
  override val child: AttachedEntityToParent?
    get() = snapshot.instrumentation.getOneChild(CHILD_CONNECTION_ID, this) as? AttachedEntityToParent
  override val childNullableParent: AttachedEntityToNullableParent?
    get() = snapshot.instrumentation.getOneChild(CHILDNULLABLEPARENT_CONNECTION_ID, this) as? AttachedEntityToNullableParent
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: MainEntityToParentData?) :
    ModifiableWorkspaceEntityBase<MainEntityToParent, MainEntityToParentData>(result), MainEntityToParentBuilder {
    internal constructor() : this(MainEntityToParentData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isXInitialized()) {
        error("Field MainEntityToParent#x should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as MainEntityToParent
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.x != dataSource.x) this.x = dataSource.x
      updateChildToParentReferences(parents)
    }

    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")
      }
    override var x: String
      get() = getEntityData().x
      set(value) {
        checkModificationAllowed()
        getEntityData(true).x = value
        changedProperty.add("x")
      }
    override var child: AttachedEntityToParentBuilder?
      get() {
        val _diff = diff
        return if (_diff != null) {
          ((_diff as MutableEntityStorageInstrumentation).getOneChildBuilder(CHILD_CONNECTION_ID, this) as? AttachedEntityToParentBuilder)
          ?: (this.entityLinks[EntityLink(true, CHILD_CONNECTION_ID)] as? AttachedEntityToParentBuilder)
        }
        else {
          (this.entityLinks[EntityLink(true, CHILD_CONNECTION_ID)] as? AttachedEntityToParentBuilder)
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          value.entityLinks[EntityLink(false, CHILD_CONNECTION_ID)] = this
          @Suppress("UNCHECKED_CAST")
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.instrumentation.replaceChildren(CHILD_CONNECTION_ID, this, listOfNotNull(value))
        }
        else {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(false, CHILD_CONNECTION_ID)] = this
          }
          this.entityLinks[EntityLink(true, CHILD_CONNECTION_ID)] = value
        }
        changedProperty.add("child")
      }
    override var childNullableParent: AttachedEntityToNullableParentBuilder?
      get() {
        val _diff = diff
        return if (_diff != null) {
          ((_diff as MutableEntityStorageInstrumentation).getOneChildBuilder(CHILDNULLABLEPARENT_CONNECTION_ID,
                                                                             this) as? AttachedEntityToNullableParentBuilder)
          ?: (this.entityLinks[EntityLink(true, CHILDNULLABLEPARENT_CONNECTION_ID)] as? AttachedEntityToNullableParentBuilder)
        }
        else {
          (this.entityLinks[EntityLink(true, CHILDNULLABLEPARENT_CONNECTION_ID)] as? AttachedEntityToNullableParentBuilder)
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          value.entityLinks[EntityLink(false, CHILDNULLABLEPARENT_CONNECTION_ID)] = this
          @Suppress("UNCHECKED_CAST")
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.instrumentation.replaceChildren(CHILDNULLABLEPARENT_CONNECTION_ID, this, listOfNotNull(value))
        }
        else {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(false, CHILDNULLABLEPARENT_CONNECTION_ID)] = this
          }
          this.entityLinks[EntityLink(true, CHILDNULLABLEPARENT_CONNECTION_ID)] = value
        }
        changedProperty.add("childNullableParent")
      }

    override fun getEntityClass(): Class<MainEntityToParent> = MainEntityToParent::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class MainEntityToParentData : WorkspaceEntityData<MainEntityToParent>() {
  lateinit var x: String
  internal fun isXInitialized(): Boolean = ::x.isInitialized
  override fun newInstance(): MainEntityToParent = MainEntityToParentImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<MainEntityToParent, *> = MainEntityToParentImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.storage.testEntities.entities.MainEntityToParent") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return MainEntityToParent::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return MainEntityToParent(x, entitySource)
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as MainEntityToParentData
    if (this.entitySource != other.entitySource) return false
    if (this.x != other.x) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as MainEntityToParentData
    if (this.x != other.x) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + x.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + x.hashCode()
    return result
  }
}
