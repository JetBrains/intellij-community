package com.intellij.workspaceModel.ide.legacyBridge.impl

import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.metadata.impl.MetadataStorageBase
import com.intellij.platform.workspace.storage.metadata.model.FinalClassMetadata
import com.intellij.platform.workspace.storage.metadata.model.OwnPropertyMetadata
import com.intellij.platform.workspace.storage.metadata.model.StorageTypeMetadata
import com.intellij.platform.workspace.storage.metadata.model.ValueTypeMetadata

@OptIn(WorkspaceEntityInternalApi::class)
internal object MetadataStorageImpl: MetadataStorageBase() {
    override fun initializeMetadata() {

        var typeMetadata: StorageTypeMetadata

        typeMetadata = FinalClassMetadata.ObjectMetadata(fqName = "com.intellij.workspaceModel.ide.legacyBridge.ModuleDependencyIndexTest\$MySource", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "virtualFileUrl", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = true, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.storage.EntitySource"))

        addMetadata(typeMetadata)

        typeMetadata = FinalClassMetadata.ObjectMetadata(fqName = "com.intellij.workspaceModel.ide.legacyBridge.LibraryLevelsTrackerTest\$MySource", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "virtualFileUrl", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = true, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.storage.EntitySource"))

        addMetadata(typeMetadata)
    }

    override fun initializeMetadataHash() {
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.EntitySource", metadataHash = -243875031)
        addMetadataHash(typeFqn = "com.intellij.workspaceModel.ide.legacyBridge.LibraryLevelsTrackerTest\$MySource", metadataHash = -1911317467)
        addMetadataHash(typeFqn = "com.intellij.workspaceModel.ide.legacyBridge.ModuleDependencyIndexTest\$MySource", metadataHash = -562697122)
    }

}
