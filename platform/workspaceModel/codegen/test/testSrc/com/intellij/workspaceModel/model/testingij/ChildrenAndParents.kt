package com.intellij.workspaceModel.storage

import org.jetbrains.deft.IntellijWsTestIj.IntellijWsTestIj
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.annotations.Child
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field
import com.intellij.workspaceModel.storage.EntitySource
import org.jetbrains.deft.Obj
import org.jetbrains.deft.impl.fields.*



// ------------------- Parent Entity --------------------------------

interface ParentEntity : WorkspaceEntity {
    val parentProperty: String

    val children: List<@Child ChildEntity>

    val childrenChildren: List<@Child ChildChildEntity>

    val optionalChildren: List<@Child ChildWithOptionalParentEntity>
    //region generated code
    //@formatter:off
    interface Builder: ParentEntity, ObjBuilder<ParentEntity> {
        override var parentProperty: String
        override var entitySource: EntitySource
        override var children: List<ChildEntity>
        override var childrenChildren: List<ChildChildEntity>
        override var optionalChildren: List<ChildWithOptionalParentEntity>
    }
    
    companion object: ObjType<ParentEntity, Builder>(IntellijWsTestIj, 10) {
        val parentProperty: Field<ParentEntity, String> = Field(this, 0, "parentProperty", TString)
        val entitySource: Field<ParentEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val children: Field<ParentEntity, List<ChildEntity>> = Field(this, 0, "children", TList(TRef("org.jetbrains.deft.IntellijWsTestIj", 11, child = true)))
        val childrenChildren: Field<ParentEntity, List<ChildChildEntity>> = Field(this, 0, "childrenChildren", TList(TRef("org.jetbrains.deft.IntellijWsTestIj", 13, child = true)))
        val optionalChildren: Field<ParentEntity, List<ChildWithOptionalParentEntity>> = Field(this, 0, "optionalChildren", TList(TRef("org.jetbrains.deft.IntellijWsTestIj", 12, child = true)))
    }
    //@formatter:on
    //endregion

}

fun WorkspaceEntityStorageBuilder.addParentEntity(parentProperty: String = "parent", source: EntitySource = MySource): ParentEntity {
    val parentEntity = ParentEntity {
        this.parentProperty = parentProperty
        this.entitySource = source
        this.children = emptyList()
        this.childrenChildren = emptyList()
        this.optionalChildren = emptyList()
    }
    this.addEntity(parentEntity)
    return parentEntity
}

// ---------------- Child entity ----------------------

data class DataClass(val stringProperty: String, val parent: EntityReference<ParentEntity>)

interface ChildEntity : WorkspaceEntity {

    val childProperty: String
    val dataClass: DataClass?

    val childrenChildren: List<@Child ChildChildEntity>

    val parentEntity: ParentEntity
    //region generated code
    //@formatter:off
    interface Builder: ChildEntity, ObjBuilder<ChildEntity> {
        override var childProperty: String
        override var entitySource: EntitySource
        override var dataClass: DataClass?
        override var childrenChildren: List<ChildChildEntity>
        override var parentEntity: ParentEntity
    }
    
    companion object: ObjType<ChildEntity, Builder>(IntellijWsTestIj, 11) {
        val childProperty: Field<ChildEntity, String> = Field(this, 0, "childProperty", TString)
        val entitySource: Field<ChildEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val dataClass: Field<ChildEntity, DataClass?> = Field(this, 0, "dataClass", TOptional(TBlob("DataClass")))
        val childrenChildren: Field<ChildEntity, List<ChildChildEntity>> = Field(this, 0, "childrenChildren", TList(TRef("org.jetbrains.deft.IntellijWsTestIj", 13, child = true)))
        val parentEntity: Field<ChildEntity, ParentEntity> = Field(this, 0, "parentEntity", TRef("org.jetbrains.deft.IntellijWsTestIj", 10))
    }
    //@formatter:on
    //endregion

}

fun WorkspaceEntityStorageBuilder.addChildEntity(
    parentEntity: ParentEntity,
    childProperty: String = "child",
    dataClass: DataClass? = null,
    source: EntitySource = MySource
) : ChildEntity {
    val childEntity = ChildEntity {
        this.parentEntity = parentEntity
        this.childProperty = childProperty
        this.dataClass = dataClass
        this.entitySource = source
        this.childrenChildren = emptyList()
    }
    this.addEntity(childEntity)
    return childEntity
}

// -------------------- Child with optional parent ---------------

interface ChildWithOptionalParentEntity : WorkspaceEntity {
    val childProperty: String
    val optionalParent: ParentEntity?
    //region generated code
    //@formatter:off
    interface Builder: ChildWithOptionalParentEntity, ObjBuilder<ChildWithOptionalParentEntity> {
        override var childProperty: String
        override var entitySource: EntitySource
        override var optionalParent: ParentEntity?
    }
    
    companion object: ObjType<ChildWithOptionalParentEntity, Builder>(IntellijWsTestIj, 12) {
        val childProperty: Field<ChildWithOptionalParentEntity, String> = Field(this, 0, "childProperty", TString)
        val entitySource: Field<ChildWithOptionalParentEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val optionalParent: Field<ChildWithOptionalParentEntity, ParentEntity?> = Field(this, 0, "optionalParent", TOptional(TRef("org.jetbrains.deft.IntellijWsTestIj", 10)))
    }
    //@formatter:on
    //endregion

}

fun WorkspaceEntityStorageBuilder.addChildWithOptionalParentEntity(
    parentEntity: ParentEntity?,
    childProperty: String = "child",
    source: EntitySource = MySource
): ChildWithOptionalParentEntity {
    val childWithOptionalParentEntity = ChildWithOptionalParentEntity {
        this.optionalParent = parentEntity
        this.childProperty = childProperty
        this.entitySource = source
    }
    this.addEntity(childWithOptionalParentEntity)
    return childWithOptionalParentEntity
}

// --------------------- Child with two parents ----------------------

interface ChildChildEntity : WorkspaceEntity {
    val parent1: ParentEntity
    val parent2: ChildEntity
    //region generated code
    //@formatter:off
    interface Builder: ChildChildEntity, ObjBuilder<ChildChildEntity> {
        override var parent1: ParentEntity
        override var entitySource: EntitySource
        override var parent2: ChildEntity
    }
    
    companion object: ObjType<ChildChildEntity, Builder>(IntellijWsTestIj, 13) {
        val parent1: Field<ChildChildEntity, ParentEntity> = Field(this, 0, "parent1", TRef("org.jetbrains.deft.IntellijWsTestIj", 10))
        val entitySource: Field<ChildChildEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val parent2: Field<ChildChildEntity, ChildEntity> = Field(this, 0, "parent2", TRef("org.jetbrains.deft.IntellijWsTestIj", 11))
    }
    //@formatter:on
    //endregion

}

fun WorkspaceEntityStorageBuilder.addChildChildEntity(parent1: ParentEntity, parent2: ChildEntity) : ChildChildEntity {
    val childChildEntity = ChildChildEntity {
        this.parent1 = parent1
        this.parent2 = parent2
        this.entitySource = MySource
    }
    this.addEntity(childChildEntity)
    return childChildEntity
}
