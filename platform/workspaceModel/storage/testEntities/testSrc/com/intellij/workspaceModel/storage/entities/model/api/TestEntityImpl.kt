// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.entity

import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.entities.model.api.*
import com.intellij.workspaceModel.storage.impl.ConnectionId
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData

@GeneratedCodeApiVersion(1)
@GeneratedCodeImplVersion(1)
open class TestEntityImpl: TestEntity, WorkspaceEntityBase() {
    
    companion object {
        
        
        val connections = listOf<ConnectionId>(
        )

    }
        
    @JvmField var _name: String? = null
    override val name: String
        get() = _name!!
                        
    override var count: Int = 0
    @JvmField var _anotherField: One? = null
    override val anotherField: One
        get() = _anotherField!!
    
    override fun connectionIdList(): List<ConnectionId> {
        return connections
    }

  override val entitySource: EntitySource
    get() = TODO("Not yet implemented")

  class Builder(result: TestEntityData?): ModifiableWorkspaceEntityBase<TestEntity, TestEntityData>(result), TestEntity.Builder {
        constructor(): this(TestEntityData())
        
        override fun applyToBuilder(builder: MutableEntityStorage) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity TestEntity is already created in a different builder")
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
            if (!getEntityData().isNameInitialized()) {
                error("Field TestEntity#name should be initialized")
            }
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field TestEntity#entitySource should be initialized")
            }
            if (!getEntityData().isAnotherFieldInitialized()) {
                error("Field TestEntity#anotherField should be initialized")
            }
        }
        
        override fun connectionIdList(): List<ConnectionId> {
            return connections
        }
    
        
        override var name: String
            get() = getEntityData().name
            set(value) {
                checkModificationAllowed()
                getEntityData().name = value
                changedProperty.add("name")
            }
            
        override var entitySource: EntitySource
            get() = getEntityData().entitySource
            set(value) {
                checkModificationAllowed()
                getEntityData().entitySource = value
                changedProperty.add("entitySource")
                
            }
            
        override var count: Int
            get() = getEntityData().count
            set(value) {
                checkModificationAllowed()
                getEntityData().count = value
                changedProperty.add("count")
            }
            
        override var anotherField: One
            get() = getEntityData().anotherField
            set(value) {
                checkModificationAllowed()
                getEntityData().anotherField = value
                changedProperty.add("anotherField")
                
            }
        
        override fun getEntityClass(): Class<TestEntity> = TestEntity::class.java
    }
}
    
class TestEntityData : WorkspaceEntityData<TestEntity>() {
    lateinit var name: String
    var count: Int = 0
    lateinit var anotherField: One

    fun isNameInitialized(): Boolean = ::name.isInitialized
    
    fun isAnotherFieldInitialized(): Boolean = ::anotherField.isInitialized

    override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<TestEntity> {
        val modifiable = TestEntityImpl.Builder(null)
        modifiable.allowModifications {
          modifiable.diff = diff
          modifiable.snapshot = diff
          modifiable.id = createEntityId()
          modifiable.entitySource = this.entitySource
        }
        modifiable.changedProperty.clear()
        return modifiable
    }

    override fun createEntity(snapshot: EntityStorage): TestEntity {
        val entity = TestEntityImpl()
        entity._name = name
        entity.count = count
        entity._anotherField = anotherField
        entity.snapshot = snapshot
        entity.id = createEntityId()
        return entity
    }

    override fun getEntityInterface(): Class<out WorkspaceEntity> {
        return TestEntity::class.java
    }

    override fun serialize(ser: EntityInformation.Serializer) {
    }

    override fun deserialize(de: EntityInformation.Deserializer) {
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as TestEntityData
        
        if (this.name != other.name) return false
        if (this.entitySource != other.entitySource) return false
        if (this.count != other.count) return false
        if (this.anotherField != other.anotherField) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this::class != other::class) return false
        
        other as TestEntityData
        
        if (this.name != other.name) return false
        if (this.count != other.count) return false
        if (this.anotherField != other.anotherField) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + count.hashCode()
        result = 31 * result + anotherField.hashCode()
        return result
    }
}