package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.metadata.impl.MetadataStorageBase
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.metadata.model.FinalClassMetadata
import com.intellij.platform.workspace.storage.metadata.model.OwnPropertyMetadata
import com.intellij.platform.workspace.storage.metadata.model.StorageTypeMetadata
import com.intellij.platform.workspace.storage.metadata.model.ValueTypeMetadata

object MetadataStorageImpl: MetadataStorageBase() {
    override fun initializeMetadata() {

        var typeMetadata: StorageTypeMetadata

        typeMetadata = EntityMetadata(fqName = "com.intellij.workspaceModel.test.api.UnknownPropertyTypeEntity", entityDataFqName = "com.intellij.workspaceModel.test.api.UnknownPropertyTypeEntityData", supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity"), properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.EntitySource")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "date", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "java.util.Date")), withDefault = false)), extProperties = listOf(), isAbstract = false)

        addMetadata(typeMetadata)
    }

    override fun initializeMetadataHash() {
        addMetadataHash(typeFqn = "com.intellij.workspaceModel.test.api.UnknownPropertyTypeEntity", metadataHash = -562800848)
    }

}
