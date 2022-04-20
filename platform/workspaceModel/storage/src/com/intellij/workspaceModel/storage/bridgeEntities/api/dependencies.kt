// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.bridgeEntities.api

import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.annotations.Child
import java.io.Serializable
import org.jetbrains.deft.Type
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion





interface LibraryEntity : WorkspaceEntityWithPersistentId {
    val name: String
    val tableId: LibraryTableId

    val roots: List<LibraryRoot>
    val excludedRoots: List<VirtualFileUrl>
    @Child val sdk: SdkEntity?
    @Child val libraryProperties: LibraryPropertiesEntity?
    @Child val libraryFilesPackagingElement: LibraryFilesPackagingElementEntity?

    override val persistentId: LibraryId
        get() = LibraryId(name, tableId)

    //region generated code
    //@formatter:off
    @GeneratedCodeApiVersion(0)
    interface Builder: LibraryEntity, ModifiableWorkspaceEntity<LibraryEntity>, ObjBuilder<LibraryEntity> {
        override var name: String
        override var entitySource: EntitySource
        override var tableId: LibraryTableId
        override var roots: List<LibraryRoot>
        override var excludedRoots: List<VirtualFileUrl>
        override var sdk: SdkEntity?
        override var libraryProperties: LibraryPropertiesEntity?
        override var libraryFilesPackagingElement: LibraryFilesPackagingElementEntity?
    }
    
    companion object: Type<LibraryEntity, Builder>()
    //@formatter:on
    //endregion

}

interface LibraryPropertiesEntity : WorkspaceEntity {
    val library: LibraryEntity

    val libraryType: String
    val propertiesXmlTag: String?


    //region generated code
    //@formatter:off
    @GeneratedCodeApiVersion(0)
    interface Builder: LibraryPropertiesEntity, ModifiableWorkspaceEntity<LibraryPropertiesEntity>, ObjBuilder<LibraryPropertiesEntity> {
        override var library: LibraryEntity
        override var entitySource: EntitySource
        override var libraryType: String
        override var propertiesXmlTag: String?
    }
    
    companion object: Type<LibraryPropertiesEntity, Builder>()
    //@formatter:on
    //endregion

}

interface SdkEntity : WorkspaceEntity {
    val library: LibraryEntity

    val homeUrl: VirtualFileUrl

    //region generated code
    //@formatter:off
    @GeneratedCodeApiVersion(0)
    interface Builder: SdkEntity, ModifiableWorkspaceEntity<SdkEntity>, ObjBuilder<SdkEntity> {
        override var library: LibraryEntity
        override var entitySource: EntitySource
        override var homeUrl: VirtualFileUrl
    }
    
    companion object: Type<SdkEntity, Builder>()
    //@formatter:on
    //endregion

}

data class LibraryRootTypeId(val name: String) : Serializable {
    companion object {
        val COMPILED = LibraryRootTypeId("CLASSES")
        val SOURCES = LibraryRootTypeId("SOURCES")
    }
}

data class LibraryRoot(
  val url: VirtualFileUrl,
  val type: LibraryRootTypeId,
  val inclusionOptions: InclusionOptions = InclusionOptions.ROOT_ITSELF
) : Serializable {
    enum class InclusionOptions {
        ROOT_ITSELF, ARCHIVES_UNDER_ROOT, ARCHIVES_UNDER_ROOT_RECURSIVELY
    }
}

