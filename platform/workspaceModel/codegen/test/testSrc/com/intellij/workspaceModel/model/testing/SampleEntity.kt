package com.intellij.workspace.model.testing

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.codegen.storage.url.VirtualFileUrl
import org.jetbrains.deft.IntellijWs.IntellijWs
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field
import org.jetbrains.deft.Obj
import org.jetbrains.deft.impl.fields.*
import org.jetbrains.deft.IntellijWsTest.IntellijWsTest



interface SampleEntity : WorkspaceEntity {
    val data: String
    val boolData: Boolean

    //region generated code
    //@formatter:off
    interface Builder: SampleEntity, ObjBuilder<SampleEntity> {
        override var data: String
        override var entitySource: EntitySource
        override var boolData: Boolean
    }
    
    companion object: ObjType<SampleEntity, Builder>(IntellijWsTest, 35) {
        val data: Field<SampleEntity, String> = Field(this, 0, "data", TString)
        val entitySource: Field<SampleEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val boolData: Field<SampleEntity, Boolean> = Field(this, 0, "boolData", TBoolean)
    }
    //@formatter:on
    //endregion
}

interface VFUEntity : WorkspaceEntity {
    val data: String
    val filePath: VirtualFileUrl?
    val directoryPath: VirtualFileUrl
    val notNullRoots: List<VirtualFileUrl>

    //region generated code
    //@formatter:off
    interface Builder: VFUEntity, ObjBuilder<VFUEntity> {
        override var data: String
        override var entitySource: EntitySource
        override var filePath: VirtualFileUrl?
        override var directoryPath: VirtualFileUrl
        override var notNullRoots: List<VirtualFileUrl>
    }
    
    companion object: ObjType<VFUEntity, Builder>(IntellijWsTest, 36) {
        val data: Field<VFUEntity, String> = Field(this, 0, "data", TString)
        val entitySource: Field<VFUEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val filePath: Field<VFUEntity, VirtualFileUrl?> = Field(this, 0, "filePath", TOptional(TBlob("VirtualFileUrl")))
        val directoryPath: Field<VFUEntity, VirtualFileUrl> = Field(this, 0, "directoryPath", TBlob("VirtualFileUrl"))
        val notNullRoots: Field<VFUEntity, List<VirtualFileUrl>> = Field(this, 0, "notNullRoots", TList(TBlob("VirtualFileUrl")))
    }
    //@formatter:on
    //endregion
}