package com.intellij.workspaceModel.storage.entities

import com.intellij.workspaceModel.codegen.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jetbrains.deft.IntellijWsTestIj.IntellijWsTestIj
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.annotations.Child
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field
import com.intellij.workspaceModel.storage.EntitySource
import org.jetbrains.deft.Obj
import org.jetbrains.deft.impl.fields.*




// ---------------------------------------

interface SampleEntity : WorkspaceEntity {
    val booleanProperty: Boolean
    val stringProperty: String
    val stringListProperty: List<String>
    val fileProperty: VirtualFileUrl

    val children: List<@Child ChildSampleEntity>

    //region generated code
    //@formatter:off
    interface Builder: SampleEntity, ObjBuilder<SampleEntity> {
        override var booleanProperty: Boolean
        override var entitySource: EntitySource
        override var stringProperty: String
        override var stringListProperty: List<String>
        override var fileProperty: VirtualFileUrl
        override var children: List<ChildSampleEntity>
    }
    
    companion object: ObjType<SampleEntity, Builder>(IntellijWsTestIj, 17) {
        val booleanProperty: Field<SampleEntity, Boolean> = Field(this, 0, "booleanProperty", TBoolean)
        val entitySource: Field<SampleEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val stringProperty: Field<SampleEntity, String> = Field(this, 0, "stringProperty", TString)
        val stringListProperty: Field<SampleEntity, List<String>> = Field(this, 0, "stringListProperty", TList(TString))
        val fileProperty: Field<SampleEntity, VirtualFileUrl> = Field(this, 0, "fileProperty", TBlob("VirtualFileUrl"))
        val children: Field<SampleEntity, List<ChildSampleEntity>> = Field(this, 0, "children", TList(TRef("org.jetbrains.deft.IntellijWsTestIj", 21, child = true)))
    }
    //@formatter:on
    //endregion

}

abstract class MyData(val myData: MyContainer)

data class MyContainer(val info: String)

fun WorkspaceEntityStorageBuilder.addSampleEntity(
  stringProperty: String,
  source: EntitySource = SampleEntitySource("test"),
  booleanProperty: Boolean = false,
  stringListProperty: MutableList<String> = ArrayList(),
  stringSetProperty: MutableSet<String> = LinkedHashSet(),
  virtualFileManager: VirtualFileUrlManager = VirtualFileUrlManagerImpl(),
  fileProperty: VirtualFileUrl = virtualFileManager.fromUrl("file:///tmp"),
  info: String = ""
): SampleEntity {
    val sampleEntity = SampleEntity {
        this.stringProperty = stringProperty
        this.entitySource = source
        this.booleanProperty = booleanProperty
        this.stringListProperty = stringListProperty
        this.fileProperty = fileProperty
        this.children = emptyList()
    }
    this.addEntity(sampleEntity)
    return sampleEntity
}

// ---------------------------------------

interface SecondSampleEntity : WorkspaceEntity {
    val intProperty: Int

    //region generated code
    //@formatter:off
    interface Builder: SecondSampleEntity, ObjBuilder<SecondSampleEntity> {
        override var intProperty: Int
        override var entitySource: EntitySource
    }
    
    companion object: ObjType<SecondSampleEntity, Builder>(IntellijWsTestIj, 18) {
        val intProperty: Field<SecondSampleEntity, Int> = Field(this, 0, "intProperty", TInt)
        val entitySource: Field<SecondSampleEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
    }
    //@formatter:on
    //endregion

}

// ---------------------------------------

interface SourceEntity : WorkspaceEntity {
    val data: String

    val children: List<@Child ChildSourceEntity>

    //region generated code
    //@formatter:off
    interface Builder: SourceEntity, ObjBuilder<SourceEntity> {
        override var data: String
        override var entitySource: EntitySource
        override var children: List<ChildSourceEntity>
    }
    
    companion object: ObjType<SourceEntity, Builder>(IntellijWsTestIj, 19) {
        val data: Field<SourceEntity, String> = Field(this, 0, "data", TString)
        val entitySource: Field<SourceEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val children: Field<SourceEntity, List<ChildSourceEntity>> = Field(this, 0, "children", TList(TRef("org.jetbrains.deft.IntellijWsTestIj", 20, child = true)))
    }
    //@formatter:on
    //endregion

}

fun WorkspaceEntityStorageBuilder.addSourceEntity(
    data: String,
    source: EntitySource
): SourceEntity {
    val sourceEntity = SourceEntity {
        this.data = data
        this.entitySource = source
        this.children = emptyList()
    }
    this.addEntity(sourceEntity)
    return sourceEntity
}

// ---------------------------------------


interface ChildSourceEntity : WorkspaceEntity {
    val data: String
    val parentEntity: SourceEntity

    //region generated code
    //@formatter:off
    interface Builder: ChildSourceEntity, ObjBuilder<ChildSourceEntity> {
        override var data: String
        override var entitySource: EntitySource
        override var parentEntity: SourceEntity
    }
    
    companion object: ObjType<ChildSourceEntity, Builder>(IntellijWsTestIj, 20) {
        val data: Field<ChildSourceEntity, String> = Field(this, 0, "data", TString)
        val entitySource: Field<ChildSourceEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val parentEntity: Field<ChildSourceEntity, SourceEntity> = Field(this, 0, "parentEntity", TRef("org.jetbrains.deft.IntellijWsTestIj", 19))
    }
    //@formatter:on
    //endregion

}

// ---------------------------------------

interface ChildSampleEntity : WorkspaceEntity {
    val data: String
    val parentEntity: SampleEntity?

    //region generated code
    //@formatter:off
    interface Builder: ChildSampleEntity, ObjBuilder<ChildSampleEntity> {
        override var data: String
        override var entitySource: EntitySource
        override var parentEntity: SampleEntity?
    }
    
    companion object: ObjType<ChildSampleEntity, Builder>(IntellijWsTestIj, 21) {
        val data: Field<ChildSampleEntity, String> = Field(this, 0, "data", TString)
        val entitySource: Field<ChildSampleEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val parentEntity: Field<ChildSampleEntity, SampleEntity?> = Field(this, 0, "parentEntity", TOptional(TRef("org.jetbrains.deft.IntellijWsTestIj", 17)))
    }
    //@formatter:on
    //endregion

}


fun WorkspaceEntityStorageBuilder.addChildSampleEntity(
    stringProperty: String,
    parent: SampleEntity?,
    source: EntitySource = SampleEntitySource("test")
): ChildSampleEntity {
    val childSampleEntity = ChildSampleEntity {
        this.data = stringProperty
        this.parentEntity = parent
        this.entitySource = source
    }
    this.addEntity(childSampleEntity)
    return childSampleEntity
}

interface PersistentIdEntity : WorkspaceEntityWithPersistentId {
    val data: String
    override val persistentId: LinkedListEntityId get() = LinkedListEntityId(data)

    //region generated code
    //@formatter:off
    interface Builder: PersistentIdEntity, ObjBuilder<PersistentIdEntity> {
        override var data: String
        override var entitySource: EntitySource
    }
    
    companion object: ObjType<PersistentIdEntity, Builder>(IntellijWsTestIj, 22) {
        val data: Field<PersistentIdEntity, String> = Field(this, 0, "data", TString)
        val entitySource: Field<PersistentIdEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val persistentId: Field<PersistentIdEntity, LinkedListEntityId> = Field(this, 0, "persistentId", TBlob("LinkedListEntityId"))
    }
    //@formatter:on
    //endregion

}


fun WorkspaceEntityStorageBuilder.addPersistentIdEntity(
    data: String,
    source: EntitySource = SampleEntitySource("test")
): PersistentIdEntity {
    val persistentIdEntity = PersistentIdEntity {
        this.data = data
        this.entitySource = source
    }
    this.addEntity(persistentIdEntity)
    return persistentIdEntity
}

interface VFUEntity : WorkspaceEntity {
    val data: String
    val fileProperty: VirtualFileUrl

    //region generated code
    //@formatter:off
    interface Builder: VFUEntity, ObjBuilder<VFUEntity> {
        override var data: String
        override var entitySource: EntitySource
        override var fileProperty: VirtualFileUrl
    }
    
    companion object: ObjType<VFUEntity, Builder>(IntellijWsTestIj, 23) {
        val data: Field<VFUEntity, String> = Field(this, 0, "data", TString)
        val entitySource: Field<VFUEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val fileProperty: Field<VFUEntity, VirtualFileUrl> = Field(this, 0, "fileProperty", TBlob("VirtualFileUrl"))
    }
    //@formatter:on
    //endregion

}

interface VFUWithTwoPropertiesEntity : WorkspaceEntity {
    val data: String
    val fileProperty: VirtualFileUrl
    val secondFileProperty: VirtualFileUrl

    //region generated code
    //@formatter:off
    interface Builder: VFUWithTwoPropertiesEntity, ObjBuilder<VFUWithTwoPropertiesEntity> {
        override var data: String
        override var entitySource: EntitySource
        override var fileProperty: VirtualFileUrl
        override var secondFileProperty: VirtualFileUrl
    }
    
    companion object: ObjType<VFUWithTwoPropertiesEntity, Builder>(IntellijWsTestIj, 24) {
        val data: Field<VFUWithTwoPropertiesEntity, String> = Field(this, 0, "data", TString)
        val entitySource: Field<VFUWithTwoPropertiesEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val fileProperty: Field<VFUWithTwoPropertiesEntity, VirtualFileUrl> = Field(this, 0, "fileProperty", TBlob("VirtualFileUrl"))
        val secondFileProperty: Field<VFUWithTwoPropertiesEntity, VirtualFileUrl> = Field(this, 0, "secondFileProperty", TBlob("VirtualFileUrl"))
    }
    //@formatter:on
    //endregion

}

interface NullableVFUEntity : WorkspaceEntity {
    val data: String
    val fileProperty: VirtualFileUrl?

    //region generated code
    //@formatter:off
    interface Builder: NullableVFUEntity, ObjBuilder<NullableVFUEntity> {
        override var data: String
        override var entitySource: EntitySource
        override var fileProperty: VirtualFileUrl?
    }
    
    companion object: ObjType<NullableVFUEntity, Builder>(IntellijWsTestIj, 25) {
        val data: Field<NullableVFUEntity, String> = Field(this, 0, "data", TString)
        val entitySource: Field<NullableVFUEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val fileProperty: Field<NullableVFUEntity, VirtualFileUrl?> = Field(this, 0, "fileProperty", TOptional(TBlob("VirtualFileUrl")))
    }
    //@formatter:on
    //endregion

}

interface ListVFUEntity : WorkspaceEntity {
    val data: String
    val fileProperty: List<VirtualFileUrl>

    //region generated code
    //@formatter:off
    interface Builder: ListVFUEntity, ObjBuilder<ListVFUEntity> {
        override var data: String
        override var entitySource: EntitySource
        override var fileProperty: List<VirtualFileUrl>
    }
    
    companion object: ObjType<ListVFUEntity, Builder>(IntellijWsTestIj, 26) {
        val data: Field<ListVFUEntity, String> = Field(this, 0, "data", TString)
        val entitySource: Field<ListVFUEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val fileProperty: Field<ListVFUEntity, List<VirtualFileUrl>> = Field(this, 0, "fileProperty", TList(TBlob("VirtualFileUrl")))
    }
    //@formatter:on
    //endregion

}

fun WorkspaceEntityStorageBuilder.addVFUEntity(
    data: String,
    fileUrl: String,
    virtualFileManager: VirtualFileUrlManager,
    source: EntitySource = SampleEntitySource("test")
): VFUEntity {
    val vfuEntity = VFUEntity {
        this.data = data
        this.fileProperty = virtualFileManager.fromUrl(fileUrl)
        this.entitySource = source
    }
    this.addEntity(vfuEntity)
    return vfuEntity
}

fun WorkspaceEntityStorageBuilder.addVFU2Entity(
    data: String,
    fileUrl: String,
    secondFileUrl: String,
    virtualFileManager: VirtualFileUrlManager,
    source: EntitySource = SampleEntitySource("test")
): VFUWithTwoPropertiesEntity {
    val vfuWithTwoPropertiesEntity = VFUWithTwoPropertiesEntity {
        this.entitySource = source
        this.data = data
        this.fileProperty = virtualFileManager.fromUrl(fileUrl)
        this.secondFileProperty = virtualFileManager.fromUrl(secondFileUrl)
    }
    this.addEntity(vfuWithTwoPropertiesEntity)
    return vfuWithTwoPropertiesEntity
}

fun WorkspaceEntityStorageBuilder.addNullableVFUEntity(
    data: String,
    fileUrl: String?,
    virtualFileManager: VirtualFileUrlManager,
    source: EntitySource = SampleEntitySource("test")
): NullableVFUEntity {
    val nullableVFUEntity = NullableVFUEntity {
        this.data = data
        this.fileProperty = fileUrl?.let { virtualFileManager.fromUrl(it) }
        this.entitySource = source
    }
    this.addEntity(nullableVFUEntity)
    return nullableVFUEntity
}

fun WorkspaceEntityStorageBuilder.addListVFUEntity(
    data: String,
    fileUrl: List<String>,
    virtualFileManager: VirtualFileUrlManager,
    source: EntitySource = SampleEntitySource("test")
): ListVFUEntity {
    val listVFUEntity = ListVFUEntity {
        this.data = data
        this.fileProperty = fileUrl.map { virtualFileManager.fromUrl(it) }
        this.entitySource = source
    }
    this.addEntity(listVFUEntity)
    return listVFUEntity
}
