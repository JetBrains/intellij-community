package com.intellij.workspaceModel.storage.bridgeEntities.api

import com.intellij.workspaceModel.storage.EntityInformation
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.GeneratedCodeImplVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.impl.ConnectionId
import com.intellij.workspaceModel.storage.impl.EntityLink
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.extractOneToManyParent
import com.intellij.workspaceModel.storage.impl.updateOneToManyParentOfChild
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child

@GeneratedCodeApiVersion(1)
@GeneratedCodeImplVersion(1)
open class JavaSourceRootEntityImpl: JavaSourceRootEntity, WorkspaceEntityBase() {
    
    companion object {
        internal val SOURCEROOT_CONNECTION_ID: ConnectionId = ConnectionId.create(SourceRootEntity::class.java, JavaSourceRootEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
        
        val connections = listOf<ConnectionId>(
            SOURCEROOT_CONNECTION_ID,
        )

    }
        
    override val sourceRoot: SourceRootEntity
        get() = snapshot.extractOneToManyParent(SOURCEROOT_CONNECTION_ID, this)!!           
        
    override var generated: Boolean = false
    @JvmField var _packagePrefix: String? = null
    override val packagePrefix: String
        get() = _packagePrefix!!
    
    override fun connectionIdList(): List<ConnectionId> {
        return connections
    }

    class Builder(val result: JavaSourceRootEntityData?): ModifiableWorkspaceEntityBase<JavaSourceRootEntity>(), JavaSourceRootEntity.Builder {
        constructor(): this(JavaSourceRootEntityData())
        
        override fun applyToBuilder(builder: MutableEntityStorage) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity JavaSourceRootEntity is already created in a different builder")
                }
            }
            
            this.diff = builder
            this.snapshot = builder
            addToBuilder()
            this.id = getEntityData().createEntityId()
            
            // Process linked entities that are connected without a builder
            processLinkedEntities(builder)
            checkInitialization() // TODO uncomment and check failed tests
        }
    
        fun checkInitialization() {
            val _diff = diff
            if (_diff != null) {
                if (_diff.extractOneToManyParent<WorkspaceEntityBase>(SOURCEROOT_CONNECTION_ID, this) == null) {
                    error("Field JavaSourceRootEntity#sourceRoot should be initialized")
                }
            }
            else {
                if (this.entityLinks[EntityLink(false, SOURCEROOT_CONNECTION_ID)] == null) {
                    error("Field JavaSourceRootEntity#sourceRoot should be initialized")
                }
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field JavaSourceRootEntity#entitySource should be initialized")
            }
            if (!getEntityData().isPackagePrefixInitialized()) {
                error("Field JavaSourceRootEntity#packagePrefix should be initialized")
            }
        }
        
        override fun connectionIdList(): List<ConnectionId> {
            return connections
        }
    
        
        override var sourceRoot: SourceRootEntity
            get() {
                val _diff = diff
                return if (_diff != null) {
                    _diff.extractOneToManyParent(SOURCEROOT_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(false, SOURCEROOT_CONNECTION_ID)]!! as SourceRootEntity
                } else {
                    this.entityLinks[EntityLink(false, SOURCEROOT_CONNECTION_ID)]!! as SourceRootEntity
                }
            }
            set(value) {
                checkModificationAllowed()
                val _diff = diff
                if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                    // Setting backref of the list
                    if (value is ModifiableWorkspaceEntityBase<*>) {
                        val data = (value.entityLinks[EntityLink(true, SOURCEROOT_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
                        value.entityLinks[EntityLink(true, SOURCEROOT_CONNECTION_ID)] = data
                    }
                    // else you're attaching a new entity to an existing entity that is not modifiable
                    _diff.addEntity(value)
                }
                if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                    _diff.updateOneToManyParentOfChild(SOURCEROOT_CONNECTION_ID, this, value)
                }
                else {
                    // Setting backref of the list
                    if (value is ModifiableWorkspaceEntityBase<*>) {
                        val data = (value.entityLinks[EntityLink(true, SOURCEROOT_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
                        value.entityLinks[EntityLink(true, SOURCEROOT_CONNECTION_ID)] = data
                    }
                    // else you're attaching a new entity to an existing entity that is not modifiable
                    
                    this.entityLinks[EntityLink(false, SOURCEROOT_CONNECTION_ID)] = value
                }
                changedProperty.add("sourceRoot")
            }
        
        override var entitySource: EntitySource
            get() = getEntityData().entitySource
            set(value) {
                checkModificationAllowed()
                getEntityData().entitySource = value
                changedProperty.add("entitySource")
                
            }
            
        override var generated: Boolean
            get() = getEntityData().generated
            set(value) {
                checkModificationAllowed()
                getEntityData().generated = value
                changedProperty.add("generated")
            }
            
        override var packagePrefix: String
            get() = getEntityData().packagePrefix
            set(value) {
                checkModificationAllowed()
                getEntityData().packagePrefix = value
                changedProperty.add("packagePrefix")
            }
        
        override fun getEntityData(): JavaSourceRootEntityData = result ?: super.getEntityData() as JavaSourceRootEntityData
        override fun getEntityClass(): Class<JavaSourceRootEntity> = JavaSourceRootEntity::class.java
    }
}
    
class JavaSourceRootEntityData : WorkspaceEntityData<JavaSourceRootEntity>() {
    var generated: Boolean = false
    lateinit var packagePrefix: String

    
    fun isPackagePrefixInitialized(): Boolean = ::packagePrefix.isInitialized

    override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<JavaSourceRootEntity> {
        val modifiable = JavaSourceRootEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        modifiable.changedProperty.clear()
        return modifiable
    }

    override fun createEntity(snapshot: EntityStorage): JavaSourceRootEntity {
        val entity = JavaSourceRootEntityImpl()
        entity.generated = generated
        entity._packagePrefix = packagePrefix
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun getEntityInterface(): Class<out WorkspaceEntity> {
        return JavaSourceRootEntity::class.java
    }

    override fun serialize(ser: EntityInformation.Serializer) {
    }

    override fun deserialize(de: EntityInformation.Deserializer) {
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as JavaSourceRootEntityData
        
        if (this.entitySource != other.entitySource) return false
        if (this.generated != other.generated) return false
        if (this.packagePrefix != other.packagePrefix) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as JavaSourceRootEntityData
        
        if (this.generated != other.generated) return false
        if (this.packagePrefix != other.packagePrefix) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + generated.hashCode()
        result = 31 * result + packagePrefix.hashCode()
        return result
    }
}