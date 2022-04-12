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
import com.intellij.workspaceModel.storage.impl.extractOneToManyChildren
import com.intellij.workspaceModel.storage.impl.extractOneToManyParent
import com.intellij.workspaceModel.storage.impl.extractOneToOneChild
import com.intellij.workspaceModel.storage.impl.updateOneToManyChildrenOfParent
import com.intellij.workspaceModel.storage.impl.updateOneToManyParentOfChild
import com.intellij.workspaceModel.storage.impl.updateOneToOneChildOfParent
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import org.jetbrains.deft.ObjBuilder

    

open class SourceRootEntityImpl: SourceRootEntity, WorkspaceEntityBase() {
    
    companion object {
        internal val CONTENTROOT_CONNECTION_ID: ConnectionId = ConnectionId.create(ContentRootEntity::class.java, SourceRootEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
        internal val CUSTOMSOURCEROOTPROPERTIES_CONNECTION_ID: ConnectionId = ConnectionId.create(SourceRootEntity::class.java, CustomSourceRootPropertiesEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
        internal val JAVASOURCEROOTS_CONNECTION_ID: ConnectionId = ConnectionId.create(SourceRootEntity::class.java, JavaSourceRootEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
        internal val JAVARESOURCEROOTS_CONNECTION_ID: ConnectionId = ConnectionId.create(SourceRootEntity::class.java, JavaResourceRootEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
    }
        
    override val contentRoot: ContentRootEntity
        get() = snapshot.extractOneToManyParent(CONTENTROOT_CONNECTION_ID, this)!!           
        
    @JvmField var _url: VirtualFileUrl? = null
    override val url: VirtualFileUrl
        get() = _url!!
                        
    @JvmField var _rootType: String? = null
    override val rootType: String
        get() = _rootType!!
                        
    override val customSourceRootProperties: CustomSourceRootPropertiesEntity?
        get() = snapshot.extractOneToOneChild(CUSTOMSOURCEROOTPROPERTIES_CONNECTION_ID, this)           
        
    override val javaSourceRoots: List<JavaSourceRootEntity>
        get() = snapshot.extractOneToManyChildren<JavaSourceRootEntity>(JAVASOURCEROOTS_CONNECTION_ID, this)!!.toList()
    
    override val javaResourceRoots: List<JavaResourceRootEntity>
        get() = snapshot.extractOneToManyChildren<JavaResourceRootEntity>(JAVARESOURCEROOTS_CONNECTION_ID, this)!!.toList()

    class Builder(val result: SourceRootEntityData?): ModifiableWorkspaceEntityBase<SourceRootEntity>(), SourceRootEntity.Builder {
        constructor(): this(SourceRootEntityData())
                 
        override fun build(): SourceRootEntity = this
        
        override fun applyToBuilder(builder: WorkspaceEntityStorageBuilder) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity SourceRootEntity is already created in a different builder")
                }
            }
            
            this.diff = builder
            this.snapshot = builder
            addToBuilder()
            this.id = getEntityData().createEntityId()
            
            index(this, "url", this.url)
            val __customSourceRootProperties = _customSourceRootProperties
            if (__customSourceRootProperties != null && __customSourceRootProperties is ModifiableWorkspaceEntityBase<*>) {
                builder.addEntity(__customSourceRootProperties)
                applyRef(CUSTOMSOURCEROOTPROPERTIES_CONNECTION_ID, __customSourceRootProperties)
                this._customSourceRootProperties = null
            }
            val __javaSourceRoots = _javaSourceRoots!!
            for (item in __javaSourceRoots) {
                if (item is ModifiableWorkspaceEntityBase<*>) {
                    builder.addEntity(item)
                }
            }
            val (withBuilder_javaSourceRoots, woBuilder_javaSourceRoots) = __javaSourceRoots.partition { it is ModifiableWorkspaceEntityBase<*> && it.diff != null }
            applyRef(JAVASOURCEROOTS_CONNECTION_ID, withBuilder_javaSourceRoots)
            this._javaSourceRoots = if (woBuilder_javaSourceRoots.isNotEmpty()) woBuilder_javaSourceRoots else null
            val __javaResourceRoots = _javaResourceRoots!!
            for (item in __javaResourceRoots) {
                if (item is ModifiableWorkspaceEntityBase<*>) {
                    builder.addEntity(item)
                }
            }
            val (withBuilder_javaResourceRoots, woBuilder_javaResourceRoots) = __javaResourceRoots.partition { it is ModifiableWorkspaceEntityBase<*> && it.diff != null }
            applyRef(JAVARESOURCEROOTS_CONNECTION_ID, withBuilder_javaResourceRoots)
            this._javaResourceRoots = if (woBuilder_javaResourceRoots.isNotEmpty()) woBuilder_javaResourceRoots else null
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
            val __contentRoot = _contentRoot
            if (__contentRoot != null && (__contentRoot is ModifiableWorkspaceEntityBase<*>) && __contentRoot.diff == null) {
                builder.addEntity(__contentRoot)
            }
            if (__contentRoot != null && (__contentRoot is ModifiableWorkspaceEntityBase<*>) && __contentRoot.diff != null) {
                // Set field to null (in referenced entity)
                val __mutSourceRoots = (__contentRoot as ContentRootEntityImpl.Builder)._sourceRoots?.toMutableList()
                __mutSourceRoots?.remove(this)
                __contentRoot._sourceRoots = if (__mutSourceRoots.isNullOrEmpty()) null else __mutSourceRoots
            }
            if (__contentRoot != null) {
                applyParentRef(CONTENTROOT_CONNECTION_ID, __contentRoot)
                this._contentRoot = null
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
                if (_diff.extractOneToManyParent<WorkspaceEntityBase>(CONTENTROOT_CONNECTION_ID, this) == null) {
                    error("Field SourceRootEntity#contentRoot should be initialized")
                }
            }
            else {
                if (_contentRoot == null) {
                    error("Field SourceRootEntity#contentRoot should be initialized")
                }
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field SourceRootEntity#entitySource should be initialized")
            }
            if (!getEntityData().isUrlInitialized()) {
                error("Field SourceRootEntity#url should be initialized")
            }
            if (!getEntityData().isRootTypeInitialized()) {
                error("Field SourceRootEntity#rootType should be initialized")
            }
            if (_diff != null) {
                if (_diff.extractOneToManyChildren<WorkspaceEntityBase>(JAVASOURCEROOTS_CONNECTION_ID, this) == null) {
                    error("Field SourceRootEntity#javaSourceRoots should be initialized")
                }
            }
            else {
                if (_javaSourceRoots == null) {
                    error("Field SourceRootEntity#javaSourceRoots should be initialized")
                }
            }
            if (_diff != null) {
                if (_diff.extractOneToManyChildren<WorkspaceEntityBase>(JAVARESOURCEROOTS_CONNECTION_ID, this) == null) {
                    error("Field SourceRootEntity#javaResourceRoots should be initialized")
                }
            }
            else {
                if (_javaResourceRoots == null) {
                    error("Field SourceRootEntity#javaResourceRoots should be initialized")
                }
            }
        }
    
        
            var _contentRoot: ContentRootEntity? = null
            override var contentRoot: ContentRootEntity
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToManyParent(CONTENTROOT_CONNECTION_ID, this) ?: _contentRoot!!
                    } else {
                        _contentRoot!!
                    }
                }
                set(value) {
                    checkModificationAllowed()
                    val _diff = diff
                    if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                        if (value is ContentRootEntityImpl.Builder) {
                            value._sourceRoots = (value._sourceRoots ?: emptyList()) + this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        _diff.addEntity(value)
                    }
                    if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                        _diff.updateOneToManyParentOfChild(CONTENTROOT_CONNECTION_ID, this, value)
                    }
                    else {
                        if (value is ContentRootEntityImpl.Builder) {
                            value._sourceRoots = (value._sourceRoots ?: emptyList()) + this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        
                        this._contentRoot = value
                    }
                    changedProperty.add("contentRoot")
                }
        
        override var entitySource: EntitySource
            get() = getEntityData().entitySource
            set(value) {
                checkModificationAllowed()
                getEntityData().entitySource = value
                changedProperty.add("entitySource")
                
            }
            
        override var url: VirtualFileUrl
            get() = getEntityData().url
            set(value) {
                checkModificationAllowed()
                getEntityData().url = value
                changedProperty.add("url")
                val _diff = diff
                if (_diff != null) index(this, "url", value)
            }
            
        override var rootType: String
            get() = getEntityData().rootType
            set(value) {
                checkModificationAllowed()
                getEntityData().rootType = value
                changedProperty.add("rootType")
            }
            
            var _customSourceRootProperties: CustomSourceRootPropertiesEntity? = null
            override var customSourceRootProperties: CustomSourceRootPropertiesEntity?
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToOneChild(CUSTOMSOURCEROOTPROPERTIES_CONNECTION_ID, this) ?: _customSourceRootProperties
                    } else {
                        _customSourceRootProperties
                    }
                }
                set(value) {
                    checkModificationAllowed()
                    val _diff = diff
                    if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                        if (value is CustomSourceRootPropertiesEntityImpl.Builder) {
                            value._sourceRoot = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        _diff.addEntity(value)
                    }
                    if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                        _diff.updateOneToOneChildOfParent(CUSTOMSOURCEROOTPROPERTIES_CONNECTION_ID, this, value)
                    }
                    else {
                        if (value is CustomSourceRootPropertiesEntityImpl.Builder) {
                            value._sourceRoot = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        
                        this._customSourceRootProperties = value
                    }
                    changedProperty.add("customSourceRootProperties")
                }
        
            var _javaSourceRoots: List<JavaSourceRootEntity>? = null
            override var javaSourceRoots: List<JavaSourceRootEntity>
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToManyChildren<JavaSourceRootEntity>(JAVASOURCEROOTS_CONNECTION_ID, this)!!.toList() + (_javaSourceRoots ?: emptyList())
                    } else {
                        _javaSourceRoots!!
                    }
                }
                set(value) {
                    checkModificationAllowed()
                    val _diff = diff
                    if (_diff != null) {
                        for (item_value in value) {
                            if (item_value is ModifiableWorkspaceEntityBase<*> && (item_value as? ModifiableWorkspaceEntityBase<*>)?.diff == null) {
                                _diff.addEntity(item_value)
                            }
                        }
                        _diff.updateOneToManyChildrenOfParent(JAVASOURCEROOTS_CONNECTION_ID, this, value)
                    }
                    else {
                        for (item_value in value) {
                            if (item_value is JavaSourceRootEntityImpl.Builder) {
                                item_value._sourceRoot = this
                            }
                            // else you're attaching a new entity to an existing entity that is not modifiable
                        }
                        
                        _javaSourceRoots = value
                        // Test
                    }
                    changedProperty.add("javaSourceRoots")
                }
        
            var _javaResourceRoots: List<JavaResourceRootEntity>? = null
            override var javaResourceRoots: List<JavaResourceRootEntity>
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToManyChildren<JavaResourceRootEntity>(JAVARESOURCEROOTS_CONNECTION_ID, this)!!.toList() + (_javaResourceRoots ?: emptyList())
                    } else {
                        _javaResourceRoots!!
                    }
                }
                set(value) {
                    checkModificationAllowed()
                    val _diff = diff
                    if (_diff != null) {
                        for (item_value in value) {
                            if (item_value is ModifiableWorkspaceEntityBase<*> && (item_value as? ModifiableWorkspaceEntityBase<*>)?.diff == null) {
                                _diff.addEntity(item_value)
                            }
                        }
                        _diff.updateOneToManyChildrenOfParent(JAVARESOURCEROOTS_CONNECTION_ID, this, value)
                    }
                    else {
                        for (item_value in value) {
                            if (item_value is JavaResourceRootEntityImpl.Builder) {
                                item_value._sourceRoot = this
                            }
                            // else you're attaching a new entity to an existing entity that is not modifiable
                        }
                        
                        _javaResourceRoots = value
                        // Test
                    }
                    changedProperty.add("javaResourceRoots")
                }
        
        override fun getEntityData(): SourceRootEntityData = result ?: super.getEntityData() as SourceRootEntityData
        override fun getEntityClass(): Class<SourceRootEntity> = SourceRootEntity::class.java
    }
    
    // TODO: Fill with the data from the current entity
    fun builder(): ObjBuilder<*> = Builder(SourceRootEntityData())
}
    
class SourceRootEntityData : WorkspaceEntityData<SourceRootEntity>() {
    lateinit var url: VirtualFileUrl
    lateinit var rootType: String

    fun isUrlInitialized(): Boolean = ::url.isInitialized
    fun isRootTypeInitialized(): Boolean = ::rootType.isInitialized

    override fun wrapAsModifiable(diff: WorkspaceEntityStorageBuilder): ModifiableWorkspaceEntity<SourceRootEntity> {
        val modifiable = SourceRootEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        return modifiable
    }

    override fun createEntity(snapshot: WorkspaceEntityStorage): SourceRootEntity {
        val entity = SourceRootEntityImpl()
        entity._url = url
        entity._rootType = rootType
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as SourceRootEntityData
        
        if (this.entitySource != other.entitySource) return false
        if (this.url != other.url) return false
        if (this.rootType != other.rootType) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as SourceRootEntityData
        
        if (this.url != other.url) return false
        if (this.rootType != other.rootType) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + url.hashCode()
        result = 31 * result + rootType.hashCode()
        return result
    }
}