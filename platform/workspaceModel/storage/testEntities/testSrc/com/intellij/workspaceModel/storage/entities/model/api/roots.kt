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
    
    companion object: ObjType<ContentRootEntity, Builder>(IntellijWs, 17)
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
    
    companion object: ObjType<SourceRootEntity, Builder>(IntellijWs, 18)
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
    
    companion object: ObjType<SourceRootOrderEntity, Builder>(IntellijWs, 19)
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
    
    companion object: ObjType<CustomSourceRootPropertiesEntity, Builder>(IntellijWs, 20)
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
    
    companion object: ObjType<JavaSourceRootEntity, Builder>(IntellijWs, 21)
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
    
    companion object: ObjType<JavaResourceRootEntity, Builder>(IntellijWs, 22)
    //@formatter:on
    //endregion

}