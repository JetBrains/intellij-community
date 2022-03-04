package com.intellij.workspace.model.api

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.impl.ConnectionId
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.extractOneToManyParent
import com.intellij.workspaceModel.storage.impl.updateOneToManyParentOfChild
import org.jetbrains.deft.*
import org.jetbrains.deft.bytes.*
import org.jetbrains.deft.collections.*
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field

    

open class JavaSourceRootEntityImpl: JavaSourceRootEntity, WorkspaceEntityBase() {
    
    companion object {
        private val SOURCEROOT_CONNECTION_ID: ConnectionId = ConnectionId.create(SourceRootEntity::class.java, JavaSourceRootEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
    }
    
    override val factory: ObjType<*, *>
        get() = JavaSourceRootEntity
        
    override val sourceRoot: SourceRootEntity
        get() = snapshot.extractOneToManyParent(SOURCEROOT_CONNECTION_ID, this)!!           
        
    override var generated: Boolean = false
    @JvmField var _packagePrefix: String? = null
    override val packagePrefix: String
        get() = _packagePrefix!!

    class Builder(val result: JavaSourceRootEntityData?): ModifiableWorkspaceEntityBase<JavaSourceRootEntity>(), JavaSourceRootEntity.Builder {
        constructor(): this(JavaSourceRootEntityData())
                 
        override val factory: ObjType<JavaSourceRootEntity, *> get() = TODO()
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
            
            
            // Adding parents and references to the parent
            val __sourceRoot = _sourceRoot
            if (__sourceRoot != null && (__sourceRoot is ModifiableWorkspaceEntityBase<*>) && __sourceRoot.diff == null) {
                builder.addEntity(__sourceRoot)
            }
            if (__sourceRoot != null && (__sourceRoot is ModifiableWorkspaceEntityBase<*>) && __sourceRoot.diff != null) {
                val __mutJavaSourceRoots = (__sourceRoot as SourceRootEntityImpl.Builder)._javaSourceRoots?.toMutableList()
                __mutJavaSourceRoots?.remove(this)
                __sourceRoot._javaSourceRoots = if (__mutJavaSourceRoots.isNullOrEmpty()) null else __mutJavaSourceRoots
            }
            if (__sourceRoot != null) {
                applyParentRef(SOURCEROOT_CONNECTION_ID, __sourceRoot)
                this._sourceRoot = null
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
                    val _diff = diff
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
                getEntityData().entitySource = value
                changedProperty.add("entitySource")
                
            }
            
        override var generated: Boolean
            get() = getEntityData().generated
            set(value) {
                getEntityData().generated = value
                changedProperty.add("generated")
            }
            
        override var packagePrefix: String
            get() = getEntityData().packagePrefix
            set(value) {
                getEntityData().packagePrefix = value
                changedProperty.add("packagePrefix")
            }
        
        override fun hasNewValue(field: Field<in JavaSourceRootEntity, *>): Boolean = TODO("Not yet implemented")                                                                     
        override fun <V> setValue(field: Field<in JavaSourceRootEntity, V>, value: V) = TODO("Not yet implemented")
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
        modifiable.diff = diff
        modifiable.snapshot = diff
        modifiable.id = createEntityId()
        modifiable.entitySource = this.entitySource
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