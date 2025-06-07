package com.intellij.platform.workspace.storage.impl

import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.metadata.impl.MetadataStorageBase
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.metadata.model.FinalClassMetadata
import com.intellij.platform.workspace.storage.metadata.model.OwnPropertyMetadata
import com.intellij.platform.workspace.storage.metadata.model.StorageTypeMetadata
import com.intellij.platform.workspace.storage.metadata.model.ValueTypeMetadata

@OptIn(WorkspaceEntityInternalApi::class)
internal object MetadataStorageImpl: MetadataStorageBase() {
    override fun initializeMetadata() {

        var typeMetadata: StorageTypeMetadata

        typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.WorkspaceEntity", entityDataFqName = "com.intellij.platform.workspace.storage.impl.WorkspaceEntityData", supertypes = listOf(), properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.EntitySource")), withDefault = false)), extProperties = listOf(), isAbstract = true)

        addMetadata(typeMetadata)

        typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId", entityDataFqName = "com.intellij.platform.workspace.storage.impl.WorkspaceEntityWithSymbolicIdData", supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity"), properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.EntitySource")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "symbolicId", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.SymbolicEntityId")), withDefault = false)), extProperties = listOf(), isAbstract = true)

        addMetadata(typeMetadata)
    }

    override fun initializeMetadataHash() {
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.WorkspaceEntity", metadataHash = 1885263617)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId", metadataHash = -272153521)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.EntitySource", metadataHash = 1637225356)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.DummyParentEntitySource", metadataHash = 1210649349)
    }

}
