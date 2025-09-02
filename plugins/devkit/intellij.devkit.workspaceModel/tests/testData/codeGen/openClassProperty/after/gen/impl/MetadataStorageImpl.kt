package com.intellij.workspaceModel.test.api.impl

import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.metadata.impl.MetadataStorageBase
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.metadata.model.ExtendableClassMetadata
import com.intellij.platform.workspace.storage.metadata.model.FinalClassMetadata
import com.intellij.platform.workspace.storage.metadata.model.OwnPropertyMetadata
import com.intellij.platform.workspace.storage.metadata.model.StorageTypeMetadata
import com.intellij.platform.workspace.storage.metadata.model.ValueTypeMetadata

@OptIn(WorkspaceEntityInternalApi::class)
internal object MetadataStorageImpl: MetadataStorageBase() {
    override fun initializeMetadata() {
        val primitiveTypeStringNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "String")

        var typeMetadata: StorageTypeMetadata

        typeMetadata = EntityMetadata(fqName = "com.intellij.workspaceModel.test.api.SimpleEntity", entityDataFqName = "com.intellij.workspaceModel.test.api.impl.SimpleEntityData", supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity"), properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.EntitySource")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "info", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "descriptor", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(fqName = "com.intellij.workspaceModel.test.api.Descriptor", subclasses = listOf(FinalClassMetadata.ClassMetadata(fqName = "com.intellij.workspaceModel.test.api.DescriptorInstance", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "data", valueType = primitiveTypeStringNotNullable, withDefault = false)), supertypes = listOf("com.intellij.workspaceModel.test.api.Descriptor"))), supertypes = listOf())), withDefault = false)), extProperties = listOf(), isAbstract = false)

        addMetadata(typeMetadata)
    }

    override fun initializeMetadataHash() {
        addMetadataHash(typeFqn = "com.intellij.workspaceModel.test.api.SimpleEntity", metadataHash = 1776285213)
        addMetadataHash(typeFqn = "com.intellij.workspaceModel.test.api.Descriptor", metadataHash = 143977406)
        addMetadataHash(typeFqn = "com.intellij.workspaceModel.test.api.DescriptorInstance", metadataHash = -211325634)
    }

}
