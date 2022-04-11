package com.intellij.workspaceModel.storage.bridgeEntities.api

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.impl.ConnectionId
import com.intellij.workspaceModel.storage.impl.ExtRefKey
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.extractOneToManyParent
import com.intellij.workspaceModel.storage.impl.updateOneToManyParentOfChild
import org.jetbrains.deft.ObjBuilder

    

open class JavaSourceRootEntityImpl: JavaSourceRootEntity, WorkspaceEntityBase() {
    
    companion object {
        internal val SOURCEROOT_CONNECTION_ID: ConnectionId = ConnectionId.create(SourceRootEntity::class.java, JavaSourceRootEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
    }
        
    override val sourceRoot: SourceRootEntity
        get() = snapshot.extractOneToManyParent(SOURCEROOT_CONNECTION_ID, this)!!           
        
    override var generated: Boolean = false
    @JvmField var _packagePrefix: String? = null
    override val packagePrefix: String
        get() = _packagePrefix!!

    class Builder(val result: JavaSourceRootEntityData?): ModifiableWorkspaceEntityBase<JavaSourceRootEntity>(), JavaSourceRootEntity.Builder {
        constructor(): this(JavaSourceRootEntityData())
                 
        override fun build(): JavaSourceRootEntity = this
        
        override fun applyToBuilder(builder: WorkspaceEntityStorageBuilder) {
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
            
            // Process entities from extension fields
            val keysToRemove = ArrayList<ExtRefKey>()
            for ((key, entity) in extReferences) {
                if (!key.isChild()) {
                    continue
                }
                if (entity is List<*>) {
                    for (item in entity) {
                        if (item is ModifiableWorkspaceEntityBase<*>) {
                            builder.addEntity(item)
                        }
                    }
                    entity as List<WorkspaceEntity>
                    val (withBuilder_entity, woBuilder_entity) = entity.partition { it is ModifiableWorkspaceEntityBase<*> && it.diff != null }
                    applyRef(key.getConnectionId(), withBuilder_entity)
                    keysToRemove.add(key)
                }
                else {
                    entity as WorkspaceEntity
                    builder.addEntity(entity)
                    applyRef(key.getConnectionId(), entity)
                    keysToRemove.add(key)
                }
            }
            for (key in keysToRemove) {
                extReferences.remove(key)
            }
            
            // Adding parents and references to the parent
            val __sourceRoot = _sourceRoot
            if (__sourceRoot != null && (__sourceRoot is ModifiableWorkspaceEntityBase<*>) && __sourceRoot.diff == null) {
                builder.addEntity(__sourceRoot)
            }
            if (__sourceRoot != null && (__sourceRoot is ModifiableWorkspaceEntityBase<*>) && __sourceRoot.diff != null) {
                // Set field to null (in referenced entity)
                val __mutJavaSourceRoots = (__sourceRoot as SourceRootEntityImpl.Builder)._javaSourceRoots?.toMutableList()
                __mutJavaSourceRoots?.remove(this)
                __sourceRoot._javaSourceRoots = if (__mutJavaSourceRoots.isNullOrEmpty()) null else __mutJavaSourceRoots
            }
            if (__sourceRoot != null) {
                applyParentRef(SOURCEROOT_CONNECTION_ID, __sourceRoot)
                this._sourceRoot = null
            }
            val parentKeysToRemove = ArrayList<ExtRefKey>()
            for ((key, entity) in extReferences) {
                if (key.isChild()) {
                    continue
                }
                if (entity is List<*>) {
                    error("Cannot have parent lists")
                }
                else {
                    entity as WorkspaceEntity
                    builder.addEntity(entity)
                    applyParentRef(key.getConnectionId(), entity)
                    parentKeysToRemove.add(key)
                }
            }
            for (key in parentKeysToRemove) {
                extReferences.remove(key)
            }
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
                if (_sourceRoot == null) {
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
    
        
            var _sourceRoot: SourceRootEntity? = null
            override var sourceRoot: SourceRootEntity
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToManyParent(SOURCEROOT_CONNECTION_ID, this) ?: _sourceRoot!!
                    } else {
                        _sourceRoot!!
                    }
                }
                set(value) {
                    checkModificationAllowed()
                    val _diff = diff
                    if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                        if (value is SourceRootEntityImpl.Builder) {
                            value._javaSourceRoots = (value._javaSourceRoots ?: emptyList()) + this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        _diff.addEntity(value)
                    }
                    if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                        _diff.updateOneToManyParentOfChild(SOURCEROOT_CONNECTION_ID, this, value)
                    }
                    else {
                        if (value is SourceRootEntityImpl.Builder) {
                            value._javaSourceRoots = (value._javaSourceRoots ?: emptyList()) + this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        
                        this._sourceRoot = value
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
    
    // TODO: Fill with the data from the current entity
    fun builder(): ObjBuilder<*> = Builder(JavaSourceRootEntityData())
}
    
class JavaSourceRootEntityData : WorkspaceEntityData<JavaSourceRootEntity>() {
    var generated: Boolean = false
    lateinit var packagePrefix: String

    
    fun isPackagePrefixInitialized(): Boolean = ::packagePrefix.isInitialized

    override fun wrapAsModifiable(diff: WorkspaceEntityStorageBuilder): ModifiableWorkspaceEntity<JavaSourceRootEntity> {
        val modifiable = JavaSourceRootEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        return modifiable
    }

    override fun createEntity(snapshot: WorkspaceEntityStorage): JavaSourceRootEntity {
        val entity = JavaSourceRootEntityImpl()
        entity.generated = generated
        entity._packagePrefix = packagePrefix
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
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