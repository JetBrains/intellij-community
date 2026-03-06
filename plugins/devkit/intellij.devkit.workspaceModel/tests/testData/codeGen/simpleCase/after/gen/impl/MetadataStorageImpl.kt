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
val primitiveTypeCharNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "Char")
val primitiveTypeLongNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "Long")
val primitiveTypeFloatNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "Float")
val primitiveTypeDoubleNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "Double")
val primitiveTypeShortNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "Short")
val primitiveTypeByteNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "Byte")

var typeMetadata: StorageTypeMetadata

typeMetadata = EntityMetadata(fqName = "com.intellij.workspaceModel.test.api.SimpleEntity", entityDataFqName = "com.intellij.workspaceModel.test.api.impl.SimpleEntityData", supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity"), properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.EntitySource")), withDefault = false),OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "version", valueType = primitiveTypeIntNotNullable, withDefault = false),OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "name", valueType = primitiveTypeStringNotNullable, withDefault = false),OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "isSimple", valueType = primitiveTypeBooleanNotNullable, withDefault = false),OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "char", valueType = primitiveTypeCharNotNullable, withDefault = false),OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "long", valueType = primitiveTypeLongNotNullable, withDefault = false),OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "float", valueType = primitiveTypeFloatNotNullable, withDefault = false),OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "double", valueType = primitiveTypeDoubleNotNullable, withDefault = false),OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "short", valueType = primitiveTypeShortNotNullable, withDefault = false),OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "byte", valueType = primitiveTypeByteNotNullable, withDefault = false)), extProperties = listOf(), isAbstract = false)

addMetadata(typeMetadata)
}

override fun initializeMetadataHash(){
addMetadataHash(typeFqn = "com.intellij.workspaceModel.test.api.SimpleEntity", metadataHash = 1469705114)
}
}
