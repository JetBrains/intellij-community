package com.intellij.platform.workspace.storage

import com.intellij.platform.workspace.storage.metadata.impl.MetadataStorageBase
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.metadata.model.FinalClassMetadata
import com.intellij.platform.workspace.storage.metadata.model.OwnPropertyMetadata
import com.intellij.platform.workspace.storage.metadata.model.StorageTypeMetadata
import com.intellij.platform.workspace.storage.metadata.model.ValueTypeMetadata

public object MetadataStorageImpl: MetadataStorageBase() {
    init {


        var typeMetadata: StorageTypeMetadata

        typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.WorkspaceEntity", entityDataFqName = "com.intellij.platform.workspace.storage.WorkspaceEntityData", supertypes = arrayListOf(), properties = arrayListOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.EntitySource")), withDefault = false)), extProperties = arrayListOf(), isAbstract = true)

        addMetadata(typeMetadata)

        typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId", entityDataFqName = "com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicIdData", supertypes = arrayListOf("com.intellij.platform.workspace.storage.WorkspaceEntity"), properties = arrayListOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.EntitySource")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "symbolicId", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.SymbolicEntityId")), withDefault = false)), extProperties = arrayListOf(), isAbstract = true)

        addMetadata(typeMetadata)
    }
}
