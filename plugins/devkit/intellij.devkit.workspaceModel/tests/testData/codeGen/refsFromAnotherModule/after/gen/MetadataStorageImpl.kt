package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.impl.ConnectionId
import com.intellij.platform.workspace.storage.metadata.impl.MetadataStorageBase
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.metadata.model.ExtPropertyMetadata
import com.intellij.platform.workspace.storage.metadata.model.FinalClassMetadata
import com.intellij.platform.workspace.storage.metadata.model.OwnPropertyMetadata
import com.intellij.platform.workspace.storage.metadata.model.StorageTypeMetadata
import com.intellij.platform.workspace.storage.metadata.model.ValueTypeMetadata

object MetadataStorageImpl: MetadataStorageBase() {
    override fun initializeMetadata() {
        val primitiveTypeIntNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "Int")
        val primitiveTypeStringNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "String")

        var typeMetadata: StorageTypeMetadata

        typeMetadata = EntityMetadata(fqName = "com.intellij.workspaceModel.test.api.ReferredEntity", entityDataFqName = "com.intellij.workspaceModel.test.api.ReferredEntityData", supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity"), properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.EntitySource")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "version", valueType = primitiveTypeIntNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "name", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "contentRoot", valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE, entityFqName = "com.intellij.platform.workspace.jps.entities.ContentRootEntity", isChild = true, isNullable = true), withDefault = false)), extProperties = listOf(ExtPropertyMetadata(isComputable = false, isOpen = false, name = "ref", receiverFqn = "com.intellij.platform.workspace.jps.entities.ContentRootEntity", valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE, entityFqName = "com.intellij.workspaceModel.test.api.ReferredEntity", isChild = false, isNullable = false), withDefault = false)), isAbstract = false)

        addMetadata(typeMetadata)
    }

    override fun initializeMetadataHash() {
        addMetadataHash(typeFqn = "com.intellij.workspaceModel.test.api.ReferredEntity", metadataHash = 1817124650)
    }

}
