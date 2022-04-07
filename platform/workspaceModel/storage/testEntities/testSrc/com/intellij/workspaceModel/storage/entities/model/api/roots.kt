package com.intellij.workspaceModel.storage.entities.model.api

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import org.jetbrains.deft.annotations.Child
import org.jetbrains.deft.Obj
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.*
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import org.jetbrains.deft.IntellijWs.IntellijWs





interface ContentRootEntity : WorkspaceEntity {
    val module: ModuleEntity

    val url: VirtualFileUrl
    val excludedUrls: List<VirtualFileUrl>
    val excludedPatterns: List<String>
    val sourceRoots: List<@Child SourceRootEntity>
    @Child val sourceRootOrder: SourceRootOrderEntity?
    //region generated code
    //@formatter:off
    interface Builder: ContentRootEntity, ModifiableWorkspaceEntity<ContentRootEntity>, ObjBuilder<ContentRootEntity> {
        override var module: ModuleEntity
        override var entitySource: EntitySource
        override var url: VirtualFileUrl
        override var excludedUrls: List<VirtualFileUrl>
        override var excludedPatterns: List<String>
        override var sourceRoots: List<SourceRootEntity>
        override var sourceRootOrder: SourceRootOrderEntity?
    }
    
    companion object: ObjType<ContentRootEntity, Builder>(IntellijWs, 17) {
        val moduleField: Field<ContentRootEntity, ModuleEntity> = Field(this, 0, "module", TRef("org.jetbrains.deft.IntellijWs", 41))
        val entitySource: Field<ContentRootEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val url: Field<ContentRootEntity, VirtualFileUrl> = Field(this, 0, "url", TBlob("VirtualFileUrl"))
        val excludedUrls: Field<ContentRootEntity, List<VirtualFileUrl>> = Field(this, 0, "excludedUrls", TList(TBlob("VirtualFileUrl")))
        val excludedPatterns: Field<ContentRootEntity, List<String>> = Field(this, 0, "excludedPatterns", TList(TString))
        val sourceRoots: Field<ContentRootEntity, List<SourceRootEntity>> = Field(this, 0, "sourceRoots", TList(TRef("org.jetbrains.deft.IntellijWs", 18, child = true)))
        val sourceRootOrder: Field<ContentRootEntity, SourceRootOrderEntity?> = Field(this, 0, "sourceRootOrder", TOptional(TRef("org.jetbrains.deft.IntellijWs", 19, child = true)))
    }
    //@formatter:on
    //endregion

}

interface SourceRootEntity : WorkspaceEntity {
    val contentRoot: ContentRootEntity

    val url: VirtualFileUrl
    val rootType: String

    @Child val customSourceRootProperties: CustomSourceRootPropertiesEntity?
    val javaSourceRoots: List<@Child JavaSourceRootEntity>?
    val javaResourceRoots: List<@Child JavaResourceRootEntity>?
    //region generated code
    //@formatter:off
    interface Builder: SourceRootEntity, ModifiableWorkspaceEntity<SourceRootEntity>, ObjBuilder<SourceRootEntity> {
        override var contentRoot: ContentRootEntity
        override var entitySource: EntitySource
        override var url: VirtualFileUrl
        override var rootType: String
        override var customSourceRootProperties: CustomSourceRootPropertiesEntity?
        override var javaSourceRoots: List<JavaSourceRootEntity>
        override var javaResourceRoots: List<JavaResourceRootEntity>
    }
    
    companion object: ObjType<SourceRootEntity, Builder>(IntellijWs, 18) {
        val contentRoot: Field<SourceRootEntity, ContentRootEntity> = Field(this, 0, "contentRoot", TRef("org.jetbrains.deft.IntellijWs", 17))
        val entitySource: Field<SourceRootEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val url: Field<SourceRootEntity, VirtualFileUrl> = Field(this, 0, "url", TBlob("VirtualFileUrl"))
        val rootType: Field<SourceRootEntity, String> = Field(this, 0, "rootType", TString)
        val customSourceRootProperties: Field<SourceRootEntity, CustomSourceRootPropertiesEntity?> = Field(this, 0, "customSourceRootProperties", TOptional(TRef("org.jetbrains.deft.IntellijWs", 20, child = true)))
        val javaSourceRoots: Field<SourceRootEntity, List<JavaSourceRootEntity>> = Field(this, 0, "javaSourceRoots", TList(TRef("org.jetbrains.deft.IntellijWs", 21, child = true)))
        val javaResourceRoots: Field<SourceRootEntity, List<JavaResourceRootEntity>> = Field(this, 0, "javaResourceRoots", TList(TRef("org.jetbrains.deft.IntellijWs", 22, child = true)))
    }
    //@formatter:on
    //endregion

}

interface SourceRootOrderEntity : WorkspaceEntity {
    val contentRootEntity: ContentRootEntity

    val orderOfSourceRoots: List<VirtualFileUrl>
    //region generated code
    //@formatter:off
    interface Builder: SourceRootOrderEntity, ModifiableWorkspaceEntity<SourceRootOrderEntity>, ObjBuilder<SourceRootOrderEntity> {
        override var contentRootEntity: ContentRootEntity
        override var entitySource: EntitySource
        override var orderOfSourceRoots: List<VirtualFileUrl>
    }
    
    companion object: ObjType<SourceRootOrderEntity, Builder>(IntellijWs, 19) {
        val contentRootEntity: Field<SourceRootOrderEntity, ContentRootEntity> = Field(this, 0, "contentRootEntity", TRef("org.jetbrains.deft.IntellijWs", 17))
        val entitySource: Field<SourceRootOrderEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val orderOfSourceRoots: Field<SourceRootOrderEntity, List<VirtualFileUrl>> = Field(this, 0, "orderOfSourceRoots", TList(TBlob("VirtualFileUrl")))
    }
    //@formatter:on
    //endregion

}

interface CustomSourceRootPropertiesEntity: WorkspaceEntity {
    val sourceRoot: SourceRootEntity

    val propertiesXmlTag: String
    //region generated code
    //@formatter:off
    interface Builder: CustomSourceRootPropertiesEntity, ModifiableWorkspaceEntity<CustomSourceRootPropertiesEntity>, ObjBuilder<CustomSourceRootPropertiesEntity> {
        override var sourceRoot: SourceRootEntity
        override var entitySource: EntitySource
        override var propertiesXmlTag: String
    }
    
    companion object: ObjType<CustomSourceRootPropertiesEntity, Builder>(IntellijWs, 20) {
        val sourceRoot: Field<CustomSourceRootPropertiesEntity, SourceRootEntity> = Field(this, 0, "sourceRoot", TRef("org.jetbrains.deft.IntellijWs", 18))
        val entitySource: Field<CustomSourceRootPropertiesEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val propertiesXmlTag: Field<CustomSourceRootPropertiesEntity, String> = Field(this, 0, "propertiesXmlTag", TString)
    }
    //@formatter:on
    //endregion
}

interface JavaSourceRootEntity : WorkspaceEntity {
    val sourceRoot: SourceRootEntity

    val generated: Boolean
    val packagePrefix: String
    //region generated code
    //@formatter:off
    interface Builder: JavaSourceRootEntity, ModifiableWorkspaceEntity<JavaSourceRootEntity>, ObjBuilder<JavaSourceRootEntity> {
        override var sourceRoot: SourceRootEntity
        override var entitySource: EntitySource
        override var generated: Boolean
        override var packagePrefix: String
    }
    
    companion object: ObjType<JavaSourceRootEntity, Builder>(IntellijWs, 21) {
        val sourceRoot: Field<JavaSourceRootEntity, SourceRootEntity> = Field(this, 0, "sourceRoot", TRef("org.jetbrains.deft.IntellijWs", 18))
        val entitySource: Field<JavaSourceRootEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val generated: Field<JavaSourceRootEntity, Boolean> = Field(this, 0, "generated", TBoolean)
        val packagePrefix: Field<JavaSourceRootEntity, String> = Field(this, 0, "packagePrefix", TString)
    }
    //@formatter:on
    //endregion

}

interface JavaResourceRootEntity: WorkspaceEntity {
    val sourceRoot: SourceRootEntity

    val generated: Boolean
    val relativeOutputPath: String
    //region generated code
    //@formatter:off
    interface Builder: JavaResourceRootEntity, ModifiableWorkspaceEntity<JavaResourceRootEntity>, ObjBuilder<JavaResourceRootEntity> {
        override var sourceRoot: SourceRootEntity
        override var entitySource: EntitySource
        override var generated: Boolean
        override var relativeOutputPath: String
    }
    
    companion object: ObjType<JavaResourceRootEntity, Builder>(IntellijWs, 22) {
        val sourceRoot: Field<JavaResourceRootEntity, SourceRootEntity> = Field(this, 0, "sourceRoot", TRef("org.jetbrains.deft.IntellijWs", 18))
        val entitySource: Field<JavaResourceRootEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val generated: Field<JavaResourceRootEntity, Boolean> = Field(this, 0, "generated", TBoolean)
        val relativeOutputPath: Field<JavaResourceRootEntity, String> = Field(this, 0, "relativeOutputPath", TString)
    }
    //@formatter:on
    //endregion

}