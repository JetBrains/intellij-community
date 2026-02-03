package com.intellij.workspaceModel.test.api.impl

import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.metadata.impl.MetadataStorageBase
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.metadata.model.FinalClassMetadata
import com.intellij.platform.workspace.storage.metadata.model.OwnPropertyMetadata
import com.intellij.platform.workspace.storage.metadata.model.StorageTypeMetadata
import com.intellij.platform.workspace.storage.metadata.model.ValueTypeMetadata

@OptIn(WorkspaceEntityInternalApi::class)
internal object MetadataStorageImpl: MetadataStorageBase(){
override fun initializeMetadata(){
val primitiveTypeStringNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "String")

var typeMetadata: StorageTypeMetadata

typeMetadata = EntityMetadata(fqName = "com.intellij.workspaceModel.test.api.EntityWithSelfRef", entityDataFqName = "com.intellij.workspaceModel.test.api.impl.EntityWithSelfRefData", supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity"), properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.EntitySource")), withDefault = false),OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "name", valueType = primitiveTypeStringNotNullable, withDefault = false),OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "parentRef", valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY, entityFqName = "com.intellij.workspaceModel.test.api.EntityWithSelfRef", isChild = false, isNullable = true), withDefault = false),OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "children", valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY, entityFqName = "com.intellij.workspaceModel.test.api.EntityWithSelfRef", isChild = true, isNullable = false), withDefault = false)), extProperties = listOf(), isAbstract = false)

addMetadata(typeMetadata)
}

override fun initializeMetadataHash(){
addMetadataHash(typeFqn = "com.intellij.workspaceModel.test.api.EntityWithSelfRef", metadataHash = -897995035)
}
}
