package model.ext

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
import com.intellij.workspaceModel.storage.impl.updateOneToManyChildrenOfParent
import org.jetbrains.deft.*
import org.jetbrains.deft.bytes.*
import org.jetbrains.deft.collections.*
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field

    

open class MainEntityParentListImpl: MainEntityParentList, WorkspaceEntityBase() {
    
    companion object {
        internal val CHILDREN_CONNECTION_ID: ConnectionId = ConnectionId.create(MainEntityParentList::class.java, AttachedEntityParentList::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, true)
    }
    
    override val factory: ObjType<*, *>
        get() = MainEntityParentList
        
    @JvmField var _x: String? = null
    override val x: String
        get() = _x!!
                        
    override val children: List<AttachedEntityParentList>
        get() = snapshot.extractOneToManyChildren<AttachedEntityParentList>(CHILDREN_CONNECTION_ID, this)!!.toList()

    class Builder(val result: MainEntityParentListData?): ModifiableWorkspaceEntityBase<MainEntityParentList>(), MainEntityParentList.Builder {
        constructor(): this(MainEntityParentListData())
                 
        override val factory: ObjType<MainEntityParentList, *> get() = TODO()
        override fun build(): MainEntityParentList = this
        
        override fun applyToBuilder(builder: WorkspaceEntityStorageBuilder) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity MainEntityParentList is already created in a different builder")
                }
            }
            
            this.diff = builder
            this.snapshot = builder
            addToBuilder()
            this.id = getEntityData().createEntityId()
            
            val __children = _children!!
            for (item in __children) {
                if (item is ModifiableWorkspaceEntityBase<*>) {
                    builder.addEntity(item)
                }
            }
            val (withBuilder_children, woBuilder_children) = __children.partition { it is ModifiableWorkspaceEntityBase<*> && it.diff != null }
            applyRef(CHILDREN_CONNECTION_ID, withBuilder_children)
            this._children = if (woBuilder_children.isNotEmpty()) woBuilder_children else null
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
            if (!getEntityData().isXInitialized()) {
                error("Field MainEntityParentList#x should be initialized")
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field MainEntityParentList#entitySource should be initialized")
            }
            if (_diff != null) {
                if (_diff.extractOneToManyChildren<WorkspaceEntityBase>(CHILDREN_CONNECTION_ID, this) == null) {
                    error("Field MainEntityParentList#children should be initialized")
                }
            }
            else {
                if (_children == null) {
                    error("Field MainEntityParentList#children should be initialized")
                }
            }
        }
    
        
        override var x: String
            get() = getEntityData().x
            set(value) {
                checkModificationAllowed()
                getEntityData().x = value
                changedProperty.add("x")
            }
            
        override var entitySource: EntitySource
            get() = getEntityData().entitySource
            set(value) {
                checkModificationAllowed()
                getEntityData().entitySource = value
                changedProperty.add("entitySource")
                
            }
            
            var _children: List<AttachedEntityParentList>? = null
            override var children: List<AttachedEntityParentList>
                get() {
                    val _diff = diff
                    return if (_diff != null) {
                        _diff.extractOneToManyChildren<AttachedEntityParentList>(CHILDREN_CONNECTION_ID, this)!!.toList() + (_children ?: emptyList())
                    } else {
                        _children!!
                    }
                }
                set(value) {
                    checkModificationAllowed()
                    val _diff = diff
                    if (_diff != null) {
                        for (item_value in value) {
                            if ((item_value as? ModifiableWorkspaceEntityBase<*>)?.diff == null) {
                                _diff.addEntity(item_value)
                            }
                        }
                        _diff.updateOneToManyChildrenOfParent(CHILDREN_CONNECTION_ID, this, value)
                    }
                    else {
                        for (item_value in value) {
                            if (item_value is AttachedEntityParentListImpl.Builder) {
                                item_value.extReferences[ExtRefKey("MainEntityParentList", "children", false, CHILDREN_CONNECTION_ID)] = this
                            }
                            // else you're attaching a new entity to an existing entity that is not modifiable
                        }
                        
                        _children = value
                        // Test
                    }
                    changedProperty.add("children")
                }
        
        override fun getEntityData(): MainEntityParentListData = result ?: super.getEntityData() as MainEntityParentListData
        override fun getEntityClass(): Class<MainEntityParentList> = MainEntityParentList::class.java
    }
    
    // TODO: Fill with the data from the current entity
    fun builder(): ObjBuilder<*> = Builder(MainEntityParentListData())
}
    
class MainEntityParentListData : WorkspaceEntityData<MainEntityParentList>() {
    lateinit var x: String

    fun isXInitialized(): Boolean = ::x.isInitialized

    override fun wrapAsModifiable(diff: WorkspaceEntityStorageBuilder): ModifiableWorkspaceEntity<MainEntityParentList> {
        val modifiable = MainEntityParentListImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        return modifiable
    }

    override fun createEntity(snapshot: WorkspaceEntityStorage): MainEntityParentList {
        val entity = MainEntityParentListImpl()
        entity._x = x
        entity.entitySource = entitySource
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as MainEntityParentListData
        
        if (this.x != other.x) return false
        if (this.entitySource != other.entitySource) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as MainEntityParentListData
        
        if (this.x != other.x) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + x.hashCode()
        return result
    }
}