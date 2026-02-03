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
val primitiveTypeIntNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "Int")
val primitiveTypeStringNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "String")
val primitiveTypeBooleanNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "Boolean")

var typeMetadata: StorageTypeMetadata

typeMetadata = FinalClassMetadata.ObjectMetadata(fqName = "com.intellij.workspaceModel.test.api.subpackage2.SimpleEntitySourceObject", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "virtualFileUrl", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = true, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.storage.EntitySource"))

addMetadata(typeMetadata)

typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "com.intellij.workspaceModel.test.api.subpackage3.SimpleEntitySourceClass", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "virtualFileUrl", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = true, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.storage.EntitySource"))

addMetadata(typeMetadata)

typeMetadata = EntityMetadata(fqName = "com.intellij.workspaceModel.test.api.SimpleEntity", entityDataFqName = "com.intellij.workspaceModel.test.api.impl.SimpleEntityData", supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity"), properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.EntitySource")), withDefault = false),OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "version", valueType = primitiveTypeIntNotNullable, withDefault = false),OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "name", valueType = primitiveTypeStringNotNullable, withDefault = false),OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "isSimple", valueType = primitiveTypeBooleanNotNullable, withDefault = false)), extProperties = listOf(), isAbstract = false)

addMetadata(typeMetadata)

typeMetadata = EntityMetadata(fqName = "com.intellij.workspaceModel.test.api.subpackage.SubSimpleEntity", entityDataFqName = "com.intellij.workspaceModel.test.api.subpackage.impl.SubSimpleEntityData", supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity"), properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.EntitySource")), withDefault = false),OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "version", valueType = primitiveTypeIntNotNullable, withDefault = false),OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "name", valueType = primitiveTypeStringNotNullable, withDefault = false),OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "isSimple", valueType = primitiveTypeBooleanNotNullable, withDefault = false)), extProperties = listOf(), isAbstract = false)

addMetadata(typeMetadata)
}

override fun initializeMetadataHash(){
addMetadataHash(typeFqn = "com.intellij.workspaceModel.test.api.SimpleEntity", metadataHash = 284369588)
addMetadataHash(typeFqn = "com.intellij.workspaceModel.test.api.subpackage.SubSimpleEntity", metadataHash = -1550163480)
addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.EntitySource", metadataHash = 1297322940)
addMetadataHash(typeFqn = "com.intellij.workspaceModel.test.api.subpackage2.SimpleEntitySourceObject", metadataHash = -1265359381)
addMetadataHash(typeFqn = "com.intellij.workspaceModel.test.api.subpackage3.SimpleEntitySourceClass", metadataHash = 940328784)
}
}
