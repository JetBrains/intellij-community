package com.intellij.workspaceModel.storage

import org.jetbrains.deft.annotations.Child
import com.intellij.workspaceModel.storage.EntitySource
import org.jetbrains.deft.IntellijWsTestIj.IntellijWsTestIj
import org.jetbrains.deft.Obj
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.*


// ------------------------------ Persistent Id ---------------

data class NameId(private val name: String) : PersistentEntityId<NamedEntity> {
    override val presentableName: String
        get() = name

    override fun toString(): String = name
}

data class AnotherNameId(private val name: String) : PersistentEntityId<NamedEntity> {
    override val presentableName: String
        get() = name

    override fun toString(): String = name
}

data class ComposedId(val name: String, val link: NameId) : PersistentEntityId<ComposedIdSoftRefEntity> {
    override val presentableName: String
        get() = "$name - ${link.presentableName}"
}

// ------------------------------ Entity With Persistent Id ------------------

interface NamedEntity : WorkspaceEntityWithPersistentId {
    val myName: String
    val additionalProperty: String?

    val children: List<@Child NamedChildEntity>

    override val persistentId: NameId
        get() = NameId(myName)
    //region generated code
    //@formatter:off
    interface Builder: NamedEntity, ObjBuilder<NamedEntity> {
        override var myName: String
        override var entitySource: EntitySource
        override var additionalProperty: String?
        override var children: List<NamedChildEntity>
    }
    
    companion object: ObjType<NamedEntity, Builder>(IntellijWsTestIj, 25) {
        val myName: Field<NamedEntity, String> = Field(this, 0, "myName", TString)
        val entitySource: Field<NamedEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val additionalProperty: Field<NamedEntity, String?> = Field(this, 0, "additionalProperty", TOptional(TString))
        val children: Field<NamedEntity, List<NamedChildEntity>> = Field(this, 0, "children", TList(TRef("org.jetbrains.deft.IntellijWsTestIj", 26, child = true)))
        val persistentId: Field<NamedEntity, NameId> = Field(this, 0, "persistentId", TBlob("NameId"))
    }
    //@formatter:on
    //endregion

}


fun WorkspaceEntityStorageBuilder.addNamedEntity(
    name: String,
    additionalProperty: String? = null,
    source: EntitySource = MySource
): NamedEntity {
    val namedEntity = NamedEntity {
        this.myName = name
        this.additionalProperty = additionalProperty
        this.entitySource = source
        this.children = emptyList()
    }
    this.addEntity(namedEntity)
    return namedEntity
}


//val NamedEntity.children: List<NamedChildEntity>
//    get() = TODO()
//  get() = referrers(NamedChildEntity::parent)

// ------------------------------ Child of entity with persistent id ------------------


interface NamedChildEntity : WorkspaceEntity {
    val childProperty: String
    val parentEntity: NamedEntity
    //region generated code
    //@formatter:off
    interface Builder: NamedChildEntity, ObjBuilder<NamedChildEntity> {
        override var childProperty: String
        override var entitySource: EntitySource
        override var parentEntity: NamedEntity
    }
    
    companion object: ObjType<NamedChildEntity, Builder>(IntellijWsTestIj, 26) {
        val childProperty: Field<NamedChildEntity, String> = Field(this, 0, "childProperty", TString)
        val entitySource: Field<NamedChildEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val parentEntity: Field<NamedChildEntity, NamedEntity> = Field(this, 0, "parentEntity", TRef("org.jetbrains.deft.IntellijWsTestIj", 25))
    }
    //@formatter:on
    //endregion

}


fun WorkspaceEntityStorageBuilder.addNamedChildEntity(
    parentEntity: NamedEntity,
    childProperty: String = "child",
    source: EntitySource = MySource
) : NamedChildEntity {
    val namedChildEntity = NamedChildEntity {
        this.parentEntity = parentEntity
        this.childProperty = childProperty
        this.entitySource = source
    }
    this.addEntity(namedChildEntity)
    return namedChildEntity
}

// ------------------------------ Entity with soft link --------------------

interface WithSoftLinkEntity : WorkspaceEntity {
    val link: NameId
    //region generated code
    //@formatter:off
    interface Builder: WithSoftLinkEntity, ObjBuilder<WithSoftLinkEntity> {
        override var link: NameId
        override var entitySource: EntitySource
    }
    
    companion object: ObjType<WithSoftLinkEntity, Builder>(IntellijWsTestIj, 27) {
        val link: Field<WithSoftLinkEntity, NameId> = Field(this, 0, "link", TBlob("NameId"))
        val entitySource: Field<WithSoftLinkEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
    }
    //@formatter:on
    //endregion

}

fun WorkspaceEntityStorageBuilder.addWithSoftLinkEntity(link: NameId, source: EntitySource = MySource): WithSoftLinkEntity {
    val withSoftLinkEntity = WithSoftLinkEntity {
        this.link = link
        this.entitySource = source
    }
    this.addEntity(withSoftLinkEntity)
    return withSoftLinkEntity
}

interface ComposedLinkEntity : WorkspaceEntity {
    val link: ComposedId
    //region generated code
    //@formatter:off
    interface Builder: ComposedLinkEntity, ObjBuilder<ComposedLinkEntity> {
        override var link: ComposedId
        override var entitySource: EntitySource
    }
    
    companion object: ObjType<ComposedLinkEntity, Builder>(IntellijWsTestIj, 28) {
        val link: Field<ComposedLinkEntity, ComposedId> = Field(this, 0, "link", TBlob("ComposedId"))
        val entitySource: Field<ComposedLinkEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
    }
    //@formatter:on
    //endregion

}

fun WorkspaceEntityStorageBuilder.addComposedLinkEntity(link: ComposedId, source: EntitySource = MySource): ComposedLinkEntity {
    val composedLinkEntity = ComposedLinkEntity {
        this.link = link
        this.entitySource = source
    }
    this.addEntity(composedLinkEntity)
    return composedLinkEntity
}

// ------------------------- Entity with persistentId and the list of soft links ------------------


interface WithListSoftLinksEntity : WorkspaceEntityWithPersistentId {
    val myName: String
    val links: List<NameId>
    override val persistentId: AnotherNameId get() = AnotherNameId(myName)
    //region generated code
    //@formatter:off
    interface Builder: WithListSoftLinksEntity, ObjBuilder<WithListSoftLinksEntity> {
        override var myName: String
        override var entitySource: EntitySource
        override var links: List<NameId>
    }
    
    companion object: ObjType<WithListSoftLinksEntity, Builder>(IntellijWsTestIj, 29) {
        val myName: Field<WithListSoftLinksEntity, String> = Field(this, 0, "myName", TString)
        val entitySource: Field<WithListSoftLinksEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val links: Field<WithListSoftLinksEntity, List<NameId>> = Field(this, 0, "links", TList(TBlob("NameId")))
        val persistentId: Field<WithListSoftLinksEntity, AnotherNameId> = Field(this, 0, "persistentId", TBlob("AnotherNameId"))
    }
    //@formatter:on
    //endregion

}


fun WorkspaceEntityStorageBuilder.addWithListSoftLinksEntity(
    name: String,
    links: List<NameId>,
    source: EntitySource = MySource
) : WithListSoftLinksEntity {
    val withListSoftLinksEntity = WithListSoftLinksEntity {
        this.myName = name
        this.links = links
        this.entitySource = source
    }
    this.addEntity(withListSoftLinksEntity)
    return withListSoftLinksEntity
}

// --------------------------- Entity with composed persistent id via soft reference ------------------


interface ComposedIdSoftRefEntity : WorkspaceEntityWithPersistentId {
    val myName: String
    val link: NameId
    override val persistentId: ComposedId get() = ComposedId(myName, link)
    //region generated code
    //@formatter:off
    interface Builder: ComposedIdSoftRefEntity, ObjBuilder<ComposedIdSoftRefEntity> {
        override var myName: String
        override var entitySource: EntitySource
        override var link: NameId
    }
    
    companion object: ObjType<ComposedIdSoftRefEntity, Builder>(IntellijWsTestIj, 30) {
        val myName: Field<ComposedIdSoftRefEntity, String> = Field(this, 0, "myName", TString)
        val entitySource: Field<ComposedIdSoftRefEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val link: Field<ComposedIdSoftRefEntity, NameId> = Field(this, 0, "link", TBlob("NameId"))
        val persistentId: Field<ComposedIdSoftRefEntity, ComposedId> = Field(this, 0, "persistentId", TBlob("ComposedId"))
    }
    //@formatter:on
    //endregion

}

fun WorkspaceEntityStorageBuilder.addComposedIdSoftRefEntity(
    name: String,
    link: NameId,
    source: EntitySource = MySource
): ComposedIdSoftRefEntity {
    val composedIdSoftRefEntity = ComposedIdSoftRefEntity {
        this.myName = name
        this.link = link
        this.entitySource = source
    }
    this.addEntity(composedIdSoftRefEntity)
    return composedIdSoftRefEntity
}
