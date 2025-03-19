// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.library.impl

import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.metadata.impl.MetadataStorageBase
import com.intellij.platform.workspace.storage.metadata.model.FinalClassMetadata
import com.intellij.platform.workspace.storage.metadata.model.OwnPropertyMetadata
import com.intellij.platform.workspace.storage.metadata.model.StorageTypeMetadata
import com.intellij.platform.workspace.storage.metadata.model.ValueTypeMetadata

@OptIn(WorkspaceEntityInternalApi::class)
internal object MetadataStorageImpl: MetadataStorageBase() {
    override fun initializeMetadata() {
        val primitiveTypeStringNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "String")
        
        var typeMetadata: StorageTypeMetadata
        
        typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "com.intellij.workspaceModel.ide.impl.legacyBridge.library.LegacyCustomLibraryEntitySource", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "levelId", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "virtualFileUrl", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = true, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.storage.EntitySource"))
        
        addMetadata(typeMetadata)
    }

    override fun initializeMetadataHash() {
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.EntitySource", metadataHash = 2042955165)
        addMetadataHash(typeFqn = "com.intellij.workspaceModel.ide.impl.legacyBridge.library.LegacyCustomLibraryEntitySource", metadataHash = 1116896941)
    }

}
