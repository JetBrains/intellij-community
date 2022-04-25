package com.intellij.workspaceModel.storage.bridgeEntities.api

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.GeneratedCodeImplVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
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

@GeneratedCodeApiVersion(0)
@GeneratedCodeImplVersion(0)
open class ContentRootEntityImpl: ContentRootEntity, WorkspaceEntityBase() {
    
    companion object {
        internal val MODULE_CONNECTION_ID: ConnectionId = ConnectionId.create(ModuleEntity::class.java, ContentRootEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
        internal val SOURCEROOTS_CONNECTION_ID: ConnectionId = ConnectionId.create(ContentRootEntity::class.java, SourceRootEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
        internal val SOURCEROOTORDER_CONNECTION_ID: ConnectionId = ConnectionId.create(ContentRootEntity::class.java, SourceRootOrderEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
    }
        
    override val module: ModuleEntity
        get() = snapshot.extractOneToManyParent(MODULE_CONNECTION_ID, this)!!           
        
    @JvmField var _url: VirtualFileUrl? = null
    override val url: VirtualFileUrl
        get() = _url!!
                        
    @JvmField var _excludedUrls: List<VirtualFileUrl>? = null
    override val excludedUrls: List<VirtualFileUrl>
        get() = _excludedUrls!!   
    
    @JvmField var _excludedPatterns: List<String>? = null
    override val excludedPatterns: List<String>
        get() = _excludedPatterns!!   
    
    override val sourceRoots: List<SourceRootEntity>
        get() = snapshot.extractOneToManyChildren<SourceRootEntity>(SOURCEROOTS_CONNECTION_ID, this)!!.toList()
    
    override val sourceRootOrder: SourceRootOrderEntity?
        get() = snapshot.extractOneToOneChild(SOURCEROOTORDER_CONNECTION_ID, this)

    class Builder(val result: ContentRootEntityData?): ModifiableWorkspaceEntityBase<ContentRootEntity>(), ContentRootEntity.Builder {
        constructor(): this(ContentRootEntityData())
                 
        override fun build(): ContentRootEntity = this
        
        override fun applyToBuilder(builder: MutableEntityStorage) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity ContentRootEntity is already created in a different builder")
                }
            }
            
            this.diff = builder
            this.snapshot = builder
            addToBuilder()
            this.id = getEntityData().createEntityId()
            
            index(this, "url", this.url)
            index(this, "excludedUrls", this.excludedUrls.toHashSet())
            val __sourceRootOrder = _sourceRootOrder
            if (__sourceRootOrder != null && __sourceRootOrder is ModifiableWorkspaceEntityBase<*>) {
                builder.addEntity(__sourceRootOrder)
                applyRef(SOURCEROOTORDER_CONNECTION_ID, __sourceRootOrder)
                this._sourceRootOrder = null
            }
            val __sourceRoots = _sourceRoots!!
            for (item in __sourceRoots) {
                if (item is ModifiableWorkspaceEntityBase<*>) {
                    builder.addEntity(item)
                }
            }
            val (withBuilder_sourceRoots, woBuilder_sourceRoots) = __sourceRoots.partition { it is ModifiableWorkspaceEntityBase<*> && it.diff != null }
            applyRef(SOURCEROOTS_CONNECTION_ID, withBuilder_sourceRoots)
            this._sourceRoots = if (woBuilder_sourceRoots.isNotEmpty()) woBuilder_sourceRoots else null
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
            val __module = _module
            if (__module != null && (__module is ModifiableWorkspaceEntityBase<*>) && __module.diff == null) {
                builder.addEntity(__module)
            }
            if (__module != null && (__module is ModifiableWorkspaceEntityBase<*>) && __module.diff != null) {
                // Set field to null (in referenced entity)
                val __mutContentRoots = (__module as ModuleEntityImpl.Builder)._contentRoots?.toMutableList()
                __mutContentRoots?.remove(this)
                __module._contentRoots = if (__mutContentRoots.isNullOrEmpty()) null else __mutContentRoots
            }
            if (__module != null) {
                applyParentRef(MODULE_CONNECTION_ID, __module)
                this._module = null
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
                if (_diff.extractOneToManyParent<WorkspaceEntityBase>(MODULE_CONNECTION_ID, this) == null) {
                    error("Field ContentRootEntity#module should be initialized")
                }
            }
            else {
                if (_module == null) {
                    error("Field ContentRootEntity#module should be initialized")
                }
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field ContentRootEntity#entitySource should be initialized")
            }
            if (!getEntityData().isUrlInitialized()) {
                error("Field ContentRootEntity#url should be initialized")
            }
            if (!getEntityData().isExcludedUrlsInitialized()) {
                error("Field ContentRootEntity#excludedUrls should be initialized")
            }
            if (!getEntityData().isExcludedPatternsInitialized()) {
                error("Field ContentRootEntity#excludedPatterns should be initialized")
            }
            if (_diff != null) {
                if (_diff.extractOneToManyChildren<WorkspaceEntityBase>(SOURCEROOTS_CONNECTION_ID, this) == null) {
                    error("Field ContentRootEntity#sourceRoots should be initialized")
                }
            }
            else {
                if (_sourceRoots == null) {
                    error("Field ContentRootEntity#sourceRoots should be initialized")
                }
            }
        }
    
        
            var _module: ModuleEntity? = null
            override var module: ModuleEntity
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToManyParent(MODULE_CONNECTION_ID, this) ?: _module!!
                    } else {
                        _module!!
                    }
                }
                set(value) {
                    checkModificationAllowed()
                    val _diff = diff
                    if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                        if (value is ModuleEntityImpl.Builder) {
                            value._contentRoots = (value._contentRoots ?: emptyList()) + this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        _diff.addEntity(value)
                    }
                    if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                        _diff.updateOneToManyParentOfChild(MODULE_CONNECTION_ID, this, value)
                    }
                    else {
                        if (value is ModuleEntityImpl.Builder) {
                            value._contentRoots = (value._contentRoots ?: emptyList()) + this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        
                        this._module = value
                    }
                    changedProperty.add("module")
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
            
        override var excludedUrls: List<VirtualFileUrl>
            get() = getEntityData().excludedUrls
            set(value) {
                checkModificationAllowed()
                getEntityData().excludedUrls = value
                val _diff = diff
                if (_diff != null) index(this, "excludedUrls", value.toHashSet())
                changedProperty.add("excludedUrls")
            }
            
        override var excludedPatterns: List<String>
            get() = getEntityData().excludedPatterns
            set(value) {
                checkModificationAllowed()
                getEntityData().excludedPatterns = value
                
                changedProperty.add("excludedPatterns")
            }
            
            var _sourceRoots: List<SourceRootEntity>? = null
            override var sourceRoots: List<SourceRootEntity>
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToManyChildren<SourceRootEntity>(SOURCEROOTS_CONNECTION_ID, this)!!.toList() + (_sourceRoots ?: emptyList())
                    } else {
                        _sourceRoots!!
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
                        _diff.updateOneToManyChildrenOfParent(SOURCEROOTS_CONNECTION_ID, this, value)
                    }
                    else {
                        for (item_value in value) {
                            if (item_value is SourceRootEntityImpl.Builder) {
                                item_value._contentRoot = this
                            }
                            // else you're attaching a new entity to an existing entity that is not modifiable
                        }
                        
                        _sourceRoots = value
                        // Test
                    }
                    changedProperty.add("sourceRoots")
                }
        
            var _sourceRootOrder: SourceRootOrderEntity? = null
            override var sourceRootOrder: SourceRootOrderEntity?
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToOneChild(SOURCEROOTORDER_CONNECTION_ID, this) ?: _sourceRootOrder
                    } else {
                        _sourceRootOrder
                    }
                }
                set(value) {
                    checkModificationAllowed()
                    val _diff = diff
                    if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
                        if (value is SourceRootOrderEntityImpl.Builder) {
                            value._contentRootEntity = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        _diff.addEntity(value)
                    }
                    if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
                        _diff.updateOneToOneChildOfParent(SOURCEROOTORDER_CONNECTION_ID, this, value)
                    }
                    else {
                        if (value is SourceRootOrderEntityImpl.Builder) {
                            value._contentRootEntity = this
                        }
                        // else you're attaching a new entity to an existing entity that is not modifiable
                        
                        this._sourceRootOrder = value
                    }
                    changedProperty.add("sourceRootOrder")
                }
        
        override fun getEntityData(): ContentRootEntityData = result ?: super.getEntityData() as ContentRootEntityData
        override fun getEntityClass(): Class<ContentRootEntity> = ContentRootEntity::class.java
    }
}
    
class ContentRootEntityData : WorkspaceEntityData<ContentRootEntity>() {
    lateinit var url: VirtualFileUrl
    lateinit var excludedUrls: List<VirtualFileUrl>
    lateinit var excludedPatterns: List<String>

    fun isUrlInitialized(): Boolean = ::url.isInitialized
    fun isExcludedUrlsInitialized(): Boolean = ::excludedUrls.isInitialized
    fun isExcludedPatternsInitialized(): Boolean = ::excludedPatterns.isInitialized

    override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<ContentRootEntity> {
        val modifiable = ContentRootEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        return modifiable
    }

    override fun createEntity(snapshot: EntityStorage): ContentRootEntity {
        val entity = ContentRootEntityImpl()
        entity._url = url
        entity._excludedUrls = excludedUrls
        entity._excludedPatterns = excludedPatterns
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun getEntityInterface(): Class<out WorkspaceEntity> {
        return ContentRootEntity::class.java
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as ContentRootEntityData
        
        if (this.entitySource != other.entitySource) return false
        if (this.url != other.url) return false
        if (this.excludedUrls != other.excludedUrls) return false
        if (this.excludedPatterns != other.excludedPatterns) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as ContentRootEntityData
        
        if (this.url != other.url) return false
        if (this.excludedUrls != other.excludedUrls) return false
        if (this.excludedPatterns != other.excludedPatterns) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + url.hashCode()
        result = 31 * result + excludedUrls.hashCode()
        result = 31 * result + excludedPatterns.hashCode()
        return result
    }
}