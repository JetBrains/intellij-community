package com.intellij.workspace.model.api

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.impl.ConnectionId
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.extractOneToOneParent
import com.intellij.workspaceModel.storage.impl.updateOneToOneParentOfChild
import org.jetbrains.deft.*
import org.jetbrains.deft.bytes.*
import org.jetbrains.deft.collections.*
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field

    

open class CustomSourceRootPropertiesEntityImpl: CustomSourceRootPropertiesEntity, WorkspaceEntityBase() {
    
    companion object {
        private val SOURCEROOT_CONNECTION_ID: ConnectionId = ConnectionId.create(SourceRootEntity::class.java, CustomSourceRootPropertiesEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
    }
    
    override val factory: ObjType<*, *>
        get() = CustomSourceRootPropertiesEntity
        
    override val sourceRoot: SourceRootEntity
        get() = snapshot.extractOneToOneParent(SOURCEROOT_CONNECTION_ID, this)!!           
        
    @JvmField var _propertiesXmlTag: String? = null
    override val propertiesXmlTag: String
        get() = _propertiesXmlTag!!

    class Builder(val result: CustomSourceRootPropertiesEntityData?): ModifiableWorkspaceEntityBase<CustomSourceRootPropertiesEntity>(), CustomSourceRootPropertiesEntity.Builder {
        constructor(): this(CustomSourceRootPropertiesEntityData())
                 
        override val factory: ObjType<CustomSourceRootPropertiesEntity, *> get() = TODO()
        override fun build(): CustomSourceRootPropertiesEntity = this
        
        override fun applyToBuilder(builder: WorkspaceEntityStorageBuilder) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity CustomSourceRootPropertiesEntity is already created in a different builder")
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
                (__sourceRoot as SourceRootEntityImpl.Builder)._customSourceRootProperties = null
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
                if (_diff.extractOneToOneParent<WorkspaceEntityBase>(SOURCEROOT_CONNECTION_ID, this) == null) {
                    error("Field CustomSourceRootPropertiesEntity#sourceRoot should be initialized")
                }
            }
            else {
                if (_sourceRoot == null) {
                    error("Field CustomSourceRootPropertiesEntity#sourceRoot should be initialized")
                }
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field CustomSourceRootPropertiesEntity#entitySource should be initialized")
            }
            if (!getEntityData().isPropertiesXmlTagInitialized()) {
                error("Field CustomSourceRootPropertiesEntity#propertiesXmlTag should be initialized")
            }
        }
    
        
            var _sourceRoot: SourceRootEntity? = null
            override var sourceRoot: SourceRootEntity
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToOneParent(SOURCEROOT_CONNECTION_ID, this) ?: _sourceRoot!!
                    } else {
                        _sourceRoot!!
                    }
                }
                set(value) {
                    val _diff = diff
                    if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                        _diff.updateOneToOneParentOfChild(SOURCEROOT_CONNECTION_ID, this, value)
                    }
                    else {
                        if (value is SourceRootEntityImpl.Builder) {
                            value._customSourceRootProperties = this
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
            
        override var propertiesXmlTag: String
            get() = getEntityData().propertiesXmlTag
            set(value) {
                getEntityData().propertiesXmlTag = value
                changedProperty.add("propertiesXmlTag")
            }
        
        override fun hasNewValue(field: Field<in CustomSourceRootPropertiesEntity, *>): Boolean = TODO("Not yet implemented")                                                                     
        override fun <V> setValue(field: Field<in CustomSourceRootPropertiesEntity, V>, value: V) = TODO("Not yet implemented")
        override fun getEntityData(): CustomSourceRootPropertiesEntityData = result ?: super.getEntityData() as CustomSourceRootPropertiesEntityData
        override fun getEntityClass(): Class<CustomSourceRootPropertiesEntity> = CustomSourceRootPropertiesEntity::class.java
    }
    
    // TODO: Fill with the data from the current entity
    fun builder(): ObjBuilder<*> = Builder(CustomSourceRootPropertiesEntityData())
}
    
class CustomSourceRootPropertiesEntityData : WorkspaceEntityData<CustomSourceRootPropertiesEntity>() {
    lateinit var propertiesXmlTag: String

    fun isPropertiesXmlTagInitialized(): Boolean = ::propertiesXmlTag.isInitialized

    override fun wrapAsModifiable(diff: WorkspaceEntityStorageBuilder): ModifiableWorkspaceEntity<CustomSourceRootPropertiesEntity> {
        val modifiable = CustomSourceRootPropertiesEntityImpl.Builder(null)
        modifiable.diff = diff
        modifiable.snapshot = diff
        modifiable.id = createEntityId()
        modifiable.entitySource = this.entitySource
        return modifiable
    }

    override fun createEntity(snapshot: WorkspaceEntityStorage): CustomSourceRootPropertiesEntity {
        val entity = CustomSourceRootPropertiesEntityImpl()
        entity._propertiesXmlTag = propertiesXmlTag
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as CustomSourceRootPropertiesEntityData
        
        if (this.entitySource != other.entitySource) return false
        if (this.propertiesXmlTag != other.propertiesXmlTag) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as CustomSourceRootPropertiesEntityData
        
        if (this.propertiesXmlTag != other.propertiesXmlTag) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + propertiesXmlTag.hashCode()
        return result
    }
}