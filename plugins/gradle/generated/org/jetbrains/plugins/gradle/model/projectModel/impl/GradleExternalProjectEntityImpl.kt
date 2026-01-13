// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model.projectModel.impl

import com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntity
import com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntityBuilder
import com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntityId
import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.SoftLinkable
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.extractOneToOneParent
import com.intellij.platform.workspace.storage.impl.indices.WorkspaceMutableIndex
import com.intellij.platform.workspace.storage.impl.updateOneToOneParentOfChild
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import org.jetbrains.plugins.gradle.model.projectModel.GradleExternalProjectEntity
import org.jetbrains.plugins.gradle.model.projectModel.GradleExternalProjectEntityBuilder
import org.jetbrains.plugins.gradle.model.projectModel.GradleExternalProjectEntityId

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class GradleExternalProjectEntityImpl(private val dataSource: GradleExternalProjectEntityData) : GradleExternalProjectEntity,
    WorkspaceEntityBase(dataSource) {

    private companion object {
        internal val EXTERNALPROJECT_CONNECTION_ID: ConnectionId = ConnectionId.create(
            ExternalProjectEntity::class.java,
            GradleExternalProjectEntity::class.java,
            ConnectionId.ConnectionType.ONE_TO_ONE,
            false
        )

        private val connections = listOf<ConnectionId>(EXTERNALPROJECT_CONNECTION_ID)

    }

    override val symbolicId: GradleExternalProjectEntityId = super.symbolicId

    override val externalProject: ExternalProjectEntity
        get() = snapshot.extractOneToOneParent(EXTERNALPROJECT_CONNECTION_ID, this)!!

    override val externalProjectId: ExternalProjectEntityId
        get() {
            readField("externalProjectId")
            return dataSource.externalProjectId
        }

    override val gradleVersion: String
        get() {
            readField("gradleVersion")
            return dataSource.gradleVersion
        }

    override val entitySource: EntitySource
        get() {
            readField("entitySource")
            return dataSource.entitySource
        }

    override fun connectionIdList(): List<ConnectionId> {
        return connections
    }


    internal class Builder(result: GradleExternalProjectEntityData?) :
        ModifiableWorkspaceEntityBase<GradleExternalProjectEntity, GradleExternalProjectEntityData>(result),
        GradleExternalProjectEntityBuilder {
        internal constructor() : this(GradleExternalProjectEntityData())

        override fun applyToBuilder(builder: MutableEntityStorage) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                } else {
                    error("Entity GradleExternalProjectEntity is already created in a different builder")
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
            if (_diff != null) {
                if (_diff.extractOneToOneParent<WorkspaceEntityBase>(EXTERNALPROJECT_CONNECTION_ID, this) == null) {
                    error("Field GradleExternalProjectEntity#externalProject should be initialized")
                }
            } else {
                if (this.entityLinks[EntityLink(false, EXTERNALPROJECT_CONNECTION_ID)] == null) {
                    error("Field GradleExternalProjectEntity#externalProject should be initialized")
                }
            }
            if (!getEntityData().isExternalProjectIdInitialized()) {
                error("Field GradleExternalProjectEntity#externalProjectId should be initialized")
            }
            if (!getEntityData().isGradleVersionInitialized()) {
                error("Field GradleExternalProjectEntity#gradleVersion should be initialized")
            }
        }

        override fun connectionIdList(): List<ConnectionId> {
            return connections
        }

        // Relabeling code, move information from dataSource to this builder
        override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
            dataSource as GradleExternalProjectEntity
            if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
            if (this.externalProjectId != dataSource.externalProjectId) this.externalProjectId = dataSource.externalProjectId
            if (this.gradleVersion != dataSource.gradleVersion) this.gradleVersion = dataSource.gradleVersion
            updateChildToParentReferences(parents)
        }


        override var entitySource: EntitySource
            get() = getEntityData().entitySource
            set(value) {
                checkModificationAllowed()
                getEntityData(true).entitySource = value
                changedProperty.add("entitySource")

            }

        override var externalProject: ExternalProjectEntityBuilder
            get() {
                val _diff = diff
                return if (_diff != null) {
                    @OptIn(EntityStorageInstrumentationApi::class)
                    ((_diff as MutableEntityStorageInstrumentation).getParentBuilder(
                        EXTERNALPROJECT_CONNECTION_ID,
                        this
                    ) as? ExternalProjectEntityBuilder)
                        ?: (this.entityLinks[EntityLink(false, EXTERNALPROJECT_CONNECTION_ID)]!! as ExternalProjectEntityBuilder)
                } else {
                    this.entityLinks[EntityLink(false, EXTERNALPROJECT_CONNECTION_ID)]!! as ExternalProjectEntityBuilder
                }
            }
            set(value) {
                checkModificationAllowed()
                val _diff = diff
                if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
                    if (value is ModifiableWorkspaceEntityBase<*, *>) {
                        value.entityLinks[EntityLink(true, EXTERNALPROJECT_CONNECTION_ID)] = this
                    }
                    // else you're attaching a new entity to an existing entity that is not modifiable
                    _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
                }
                if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
                    _diff.updateOneToOneParentOfChild(EXTERNALPROJECT_CONNECTION_ID, this, value)
                } else {
                    if (value is ModifiableWorkspaceEntityBase<*, *>) {
                        value.entityLinks[EntityLink(true, EXTERNALPROJECT_CONNECTION_ID)] = this
                    }
                    // else you're attaching a new entity to an existing entity that is not modifiable

                    this.entityLinks[EntityLink(false, EXTERNALPROJECT_CONNECTION_ID)] = value
                }
                changedProperty.add("externalProject")
            }

        override var externalProjectId: ExternalProjectEntityId
            get() = getEntityData().externalProjectId
            set(value) {
                checkModificationAllowed()
                getEntityData(true).externalProjectId = value
                changedProperty.add("externalProjectId")

            }

        override var gradleVersion: String
            get() = getEntityData().gradleVersion
            set(value) {
                checkModificationAllowed()
                getEntityData(true).gradleVersion = value
                changedProperty.add("gradleVersion")
            }

        override fun getEntityClass(): Class<GradleExternalProjectEntity> = GradleExternalProjectEntity::class.java
    }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class GradleExternalProjectEntityData : WorkspaceEntityData<GradleExternalProjectEntity>(), SoftLinkable {
    lateinit var externalProjectId: ExternalProjectEntityId
    lateinit var gradleVersion: String

    internal fun isExternalProjectIdInitialized(): Boolean = ::externalProjectId.isInitialized
    internal fun isGradleVersionInitialized(): Boolean = ::gradleVersion.isInitialized

    override fun getLinks(): Set<SymbolicEntityId<*>> {
        val result = HashSet<SymbolicEntityId<*>>()
        result.add(externalProjectId)
        return result
    }

    override fun index(index: WorkspaceMutableIndex<SymbolicEntityId<*>>) {
        index.index(this, externalProjectId)
    }

    override fun updateLinksIndex(prev: Set<SymbolicEntityId<*>>, index: WorkspaceMutableIndex<SymbolicEntityId<*>>) {
        // TODO verify logic
        val mutablePreviousSet = HashSet(prev)
        val removedItem_externalProjectId = mutablePreviousSet.remove(externalProjectId)
        if (!removedItem_externalProjectId) {
            index.index(this, externalProjectId)
        }
        for (removed in mutablePreviousSet) {
            index.remove(this, removed)
        }
    }

    override fun updateLink(oldLink: SymbolicEntityId<*>, newLink: SymbolicEntityId<*>): Boolean {
        var changed = false
        val externalProjectId_data = if (externalProjectId == oldLink) {
            changed = true
            newLink as ExternalProjectEntityId
        } else {
            null
        }
        if (externalProjectId_data != null) {
            externalProjectId = externalProjectId_data
        }
        return changed
    }

    override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntityBuilder<GradleExternalProjectEntity> {
        val modifiable = GradleExternalProjectEntityImpl.Builder(null)
        modifiable.diff = diff
        modifiable.id = createEntityId()
        return modifiable
    }

    @OptIn(EntityStorageInstrumentationApi::class)
    override fun createEntity(snapshot: EntityStorageInstrumentation): GradleExternalProjectEntity {
        val entityId = createEntityId()
        return snapshot.initializeEntity(entityId) {
            val entity = GradleExternalProjectEntityImpl(this)
            entity.snapshot = snapshot
            entity.id = entityId
            entity
        }
    }

    override fun getMetadata(): EntityMetadata {
        return MetadataStorageImpl.getMetadataByTypeFqn("org.jetbrains.plugins.gradle.model.projectModel.GradleExternalProjectEntity") as EntityMetadata
    }

    override fun getEntityInterface(): Class<out WorkspaceEntity> {
        return GradleExternalProjectEntity::class.java
    }

    override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
        return GradleExternalProjectEntity(externalProjectId, gradleVersion, entitySource) {
            parents.filterIsInstance<ExternalProjectEntityBuilder>().singleOrNull()?.let { this.externalProject = it }
        }
    }

    override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
        val res = mutableListOf<Class<out WorkspaceEntity>>()
        res.add(ExternalProjectEntity::class.java)
        return res
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this.javaClass != other.javaClass) return false

        other as GradleExternalProjectEntityData

        if (this.entitySource != other.entitySource) return false
        if (this.externalProjectId != other.externalProjectId) return false
        if (this.gradleVersion != other.gradleVersion) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this.javaClass != other.javaClass) return false

        other as GradleExternalProjectEntityData

        if (this.externalProjectId != other.externalProjectId) return false
        if (this.gradleVersion != other.gradleVersion) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + externalProjectId.hashCode()
        result = 31 * result + gradleVersion.hashCode()
        return result
    }

    override fun hashCodeIgnoringEntitySource(): Int {
        var result = javaClass.hashCode()
        result = 31 * result + externalProjectId.hashCode()
        result = 31 * result + gradleVersion.hashCode()
        return result
    }
}
