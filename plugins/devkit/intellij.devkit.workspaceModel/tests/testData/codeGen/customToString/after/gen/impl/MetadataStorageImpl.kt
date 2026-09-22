package com.intellij.workspaceModel.test.api.impl

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
val primitiveTypeIntNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "Int")
var typeMetadata: StorageTypeMetadata
typeMetadata = EntityMetadata(fqName = "com.intellij.workspaceModel.test.api.WithCustomToStringEntity", entityDataFqName = "com.intellij.workspaceModel.test.api.impl.WithCustomToStringEntityData", supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity"), properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.EntitySource")), withDefault = false),OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "myName", valueType = primitiveTypeStringNotNullable, withDefault = false),OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "version", valueType = primitiveTypeIntNotNullable, withDefault = false)), extProperties = listOf(), isAbstract = false)
addMetadata(typeMetadata)
}
override fun initializeMetadataHash(){
addMetadataHash(typeFqn = "com.intellij.workspaceModel.test.api.WithCustomToStringEntity", metadataHash = -1794088474)
}
}
