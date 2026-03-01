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

var typeMetadata: StorageTypeMetadata

typeMetadata = EntityMetadata(fqName = "com.intellij.workspaceModel.test.api.ChildEntity", entityDataFqName = "com.intellij.workspaceModel.test.api.impl.ChildEntityData", supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity","com.intellij.workspaceModel.test.api.GrandParentEntity","com.intellij.workspaceModel.test.api.ParentEntity"), properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.EntitySource")), withDefault = false),OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "data1", valueType = primitiveTypeStringNotNullable, withDefault = false),OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "data2", valueType = primitiveTypeStringNotNullable, withDefault = false),OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "data3", valueType = primitiveTypeStringNotNullable, withDefault = false)), extProperties = listOf(), isAbstract = false)

addMetadata(typeMetadata)

typeMetadata = EntityMetadata(fqName = "com.intellij.workspaceModel.test.api.GrandParentEntity", entityDataFqName = "com.intellij.workspaceModel.test.api.impl.GrandParentEntityData", supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity"), properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.EntitySource")), withDefault = false),OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "data1", valueType = primitiveTypeStringNotNullable, withDefault = false)), extProperties = listOf(), isAbstract = true)

addMetadata(typeMetadata)

typeMetadata = EntityMetadata(fqName = "com.intellij.workspaceModel.test.api.ParentEntity", entityDataFqName = "com.intellij.workspaceModel.test.api.impl.ParentEntityData", supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity","com.intellij.workspaceModel.test.api.GrandParentEntity"), properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.EntitySource")), withDefault = false),OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "data1", valueType = primitiveTypeStringNotNullable, withDefault = false),OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "data2", valueType = primitiveTypeStringNotNullable, withDefault = false)), extProperties = listOf(), isAbstract = false)

addMetadata(typeMetadata)
}

override fun initializeMetadataHash(){
addMetadataHash(typeFqn = "com.intellij.workspaceModel.test.api.ChildEntity", metadataHash = -1983600684)
addMetadataHash(typeFqn = "com.intellij.workspaceModel.test.api.GrandParentEntity", metadataHash = 778847350)
addMetadataHash(typeFqn = "com.intellij.workspaceModel.test.api.ParentEntity", metadataHash = -259766281)
}
}
