// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.modules.impl

import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.SoftLinkable
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.indices.WorkspaceMutableIndex
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptCompilationConfigurationEntity
import org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptCompilationConfigurationEntityBuilder
import org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptCompilationConfigurationIdentity

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class ScriptCompilationConfigurationEntityImpl(private val dataSource: ScriptCompilationConfigurationEntityData) :
    ScriptCompilationConfigurationEntity, WorkspaceEntityBase(dataSource) {

    private companion object {

        private val connections = listOf<ConnectionId>()

    }

    override val symbolicId: ScriptCompilationConfigurationIdentity = super.symbolicId

    override val data: ByteArray
        get() {
            readField("data")
            return dataSource.data
        }
    override val identity: ScriptCompilationConfigurationIdentity
        get() {
            readField("identity")
            return dataSource.identity
        }

    override val entitySource: EntitySource
        get() {
            readField("entitySource")
            return dataSource.entitySource
        }

    override fun connectionIdList(): List<ConnectionId> {
        return connections
    }


    internal class Builder(result: ScriptCompilationConfigurationEntityData?) :
        ModifiableWorkspaceEntityBase<ScriptCompilationConfigurationEntity, ScriptCompilationConfigurationEntityData>(result),
        ScriptCompilationConfigurationEntityBuilder {
        internal constructor() : this(ScriptCompilationConfigurationEntityData())

        override fun applyToBuilder(builder: MutableEntityStorage) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                } else {
                    error("Entity ScriptCompilationConfigurationEntity is already created in a different builder")
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
            if (!getEntityData().isDataInitialized()) {
                error("Field ScriptCompilationConfigurationEntity#data should be initialized")
            }
            if (!getEntityData().isIdentityInitialized()) {
                error("Field ScriptCompilationConfigurationEntity#identity should be initialized")
            }
        }

        override fun connectionIdList(): List<ConnectionId> {
            return connections
        }

        // Relabeling code, move information from dataSource to this builder
        override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
            dataSource as ScriptCompilationConfigurationEntity
            if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
            if (this.data != dataSource.data) this.data = dataSource.data
            if (this.identity != dataSource.identity) this.identity = dataSource.identity
            updateChildToParentReferences(parents)
        }


        override var entitySource: EntitySource
            get() = getEntityData().entitySource
            set(value) {
                checkModificationAllowed()
                getEntityData(true).entitySource = value
                changedProperty.add("entitySource")

            }
        override var data: ByteArray
            get() = getEntityData().data
            set(value) {
                checkModificationAllowed()
                getEntityData(true).data = value
                changedProperty.add("data")

            }
        override var identity: ScriptCompilationConfigurationIdentity
            get() = getEntityData().identity
            set(value) {
                checkModificationAllowed()
                getEntityData(true).identity = value
                changedProperty.add("identity")

            }

        override fun getEntityClass(): Class<ScriptCompilationConfigurationEntity> = ScriptCompilationConfigurationEntity::class.java
    }

}

@OptIn(WorkspaceEntityInternalApi::class)
internal class ScriptCompilationConfigurationEntityData : WorkspaceEntityData<ScriptCompilationConfigurationEntity>(), SoftLinkable {
    lateinit var data: ByteArray
    lateinit var identity: ScriptCompilationConfigurationIdentity

    internal fun isDataInitialized(): Boolean = ::data.isInitialized
    internal fun isIdentityInitialized(): Boolean = ::identity.isInitialized

    override fun getLinks(): Set<SymbolicEntityId<*>> {
        val result = HashSet<SymbolicEntityId<*>>()
        result.add(identity)
        return result
    }

    override fun index(index: WorkspaceMutableIndex<SymbolicEntityId<*>>) {
        index.index(this, identity)
    }

    override fun updateLinksIndex(prev: Set<SymbolicEntityId<*>>, index: WorkspaceMutableIndex<SymbolicEntityId<*>>) {
// TODO verify logic
        val mutablePreviousSet = HashSet(prev)
        val removedItem_identity = mutablePreviousSet.remove(identity)
        if (!removedItem_identity) {
            index.index(this, identity)
        }
        for (removed in mutablePreviousSet) {
            index.remove(this, removed)
        }
    }

    override fun updateLink(oldLink: SymbolicEntityId<*>, newLink: SymbolicEntityId<*>): Boolean {
        var changed = false
        val identity_data = if (identity == oldLink) {
            changed = true
            newLink as ScriptCompilationConfigurationIdentity
        } else {
            null
        }
        if (identity_data != null) {
            identity = identity_data
        }
        return changed
    }

    override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntityBuilder<ScriptCompilationConfigurationEntity> {
        val modifiable = ScriptCompilationConfigurationEntityImpl.Builder(null)
        modifiable.diff = diff
        modifiable.id = createEntityId()
        return modifiable
    }

    @OptIn(EntityStorageInstrumentationApi::class)
    override fun createEntity(snapshot: EntityStorageInstrumentation): ScriptCompilationConfigurationEntity {
        val entityId = createEntityId()
        return snapshot.initializeEntity(entityId) {
            val entity = ScriptCompilationConfigurationEntityImpl(this)
            entity.snapshot = snapshot
            entity.id = entityId
            entity
        }
    }

    override fun getMetadata(): EntityMetadata {
        return MetadataStorageImpl.getMetadataByTypeFqn("org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptCompilationConfigurationEntity") as EntityMetadata
    }

    override fun getEntityInterface(): Class<out WorkspaceEntity> {
        return ScriptCompilationConfigurationEntity::class.java
    }

    override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
        return ScriptCompilationConfigurationEntity(data, identity, entitySource)
    }

    override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
        val res = mutableListOf<Class<out WorkspaceEntity>>()
        return res
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this.javaClass != other.javaClass) return false
        other as ScriptCompilationConfigurationEntityData
        if (this.entitySource != other.entitySource) return false
        if (this.data != other.data) return false
        if (this.identity != other.identity) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this.javaClass != other.javaClass) return false
        other as ScriptCompilationConfigurationEntityData
        if (this.data != other.data) return false
        if (this.identity != other.identity) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + data.hashCode()
        result = 31 * result + identity.hashCode()
        return result
    }

    override fun hashCodeIgnoringEntitySource(): Int {
        var result = javaClass.hashCode()
        result = 31 * result + data.hashCode()
        result = 31 * result + identity.hashCode()
        return result
    }
}
