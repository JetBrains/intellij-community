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
        val primitiveTypeIntNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "Int")
        val primitiveTypeListNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "List")
        val primitiveTypeSetNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "Set")

        var typeMetadata: StorageTypeMetadata

        typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "com.intellij.workspaceModel.test.api.SimpleId", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "name", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "presentableName", valueType = primitiveTypeStringNotNullable, withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId"))

        addMetadata(typeMetadata)

        typeMetadata = EntityMetadata(fqName = "com.intellij.workspaceModel.test.api.SimpleSymbolicIdEntity", entityDataFqName = "com.intellij.workspaceModel.test.api.impl.SimpleSymbolicIdEntityData", supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity",
"com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId"), properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.EntitySource")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "version", valueType = primitiveTypeIntNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "name", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "related", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "com.intellij.workspaceModel.test.api.SimpleId", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "name", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "presentableName", valueType = primitiveTypeStringNotNullable, withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId"))), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "sealedClassWithLinks", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(fqName = "com.intellij.workspaceModel.test.api.SealedClassWithLinks", subclasses = listOf(FinalClassMetadata.ClassMetadata(fqName = "com.intellij.workspaceModel.test.api.SealedClassWithLinks\$Many\$Ordered", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "list", valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "com.intellij.workspaceModel.test.api.SimpleId", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "name", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "presentableName", valueType = primitiveTypeStringNotNullable, withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId")))), primitive = primitiveTypeListNotNullable), withDefault = false)), supertypes = listOf("com.intellij.workspaceModel.test.api.SealedClassWithLinks",
"com.intellij.workspaceModel.test.api.SealedClassWithLinks\$Many")),
FinalClassMetadata.ClassMetadata(fqName = "com.intellij.workspaceModel.test.api.SealedClassWithLinks\$Many\$Unordered", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "set", valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.workspaceModel.test.api.SimpleId"))), primitive = primitiveTypeSetNotNullable), withDefault = false)), supertypes = listOf("com.intellij.workspaceModel.test.api.SealedClassWithLinks",
"com.intellij.workspaceModel.test.api.SealedClassWithLinks\$Many")),
FinalClassMetadata.ObjectMetadata(fqName = "com.intellij.workspaceModel.test.api.SealedClassWithLinks\$Nothing", properties = listOf(), supertypes = listOf("com.intellij.workspaceModel.test.api.SealedClassWithLinks")),
FinalClassMetadata.ClassMetadata(fqName = "com.intellij.workspaceModel.test.api.SealedClassWithLinks\$Single", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "id", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.workspaceModel.test.api.SimpleId")), withDefault = false)), supertypes = listOf("com.intellij.workspaceModel.test.api.SealedClassWithLinks"))), supertypes = listOf())), withDefault = false),
OwnPropertyMetadata(isComputable = true, isKey = false, isOpen = false, name = "symbolicId", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "com.intellij.workspaceModel.test.api.SimpleId", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "name", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "presentableName", valueType = primitiveTypeStringNotNullable, withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId"))), withDefault = false)), extProperties = listOf(), isAbstract = false)

        addMetadata(typeMetadata)
    }

    override fun initializeMetadataHash() {
        addMetadataHash(typeFqn = "com.intellij.workspaceModel.test.api.SimpleSymbolicIdEntity", metadataHash = -99546289)
        addMetadataHash(typeFqn = "com.intellij.workspaceModel.test.api.SimpleId", metadataHash = 1096965603)
        addMetadataHash(typeFqn = "com.intellij.workspaceModel.test.api.SealedClassWithLinks", metadataHash = 229222369)
        addMetadataHash(typeFqn = "com.intellij.workspaceModel.test.api.SealedClassWithLinks\$Many", metadataHash = 372382182)
        addMetadataHash(typeFqn = "com.intellij.workspaceModel.test.api.SealedClassWithLinks\$Many\$Ordered", metadataHash = -1632944095)
        addMetadataHash(typeFqn = "com.intellij.workspaceModel.test.api.SealedClassWithLinks\$Many\$Unordered", metadataHash = -585653981)
        addMetadataHash(typeFqn = "com.intellij.workspaceModel.test.api.SealedClassWithLinks\$Nothing", metadataHash = 1541593304)
        addMetadataHash(typeFqn = "com.intellij.workspaceModel.test.api.SealedClassWithLinks\$Single", metadataHash = -800484835)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.SymbolicEntityId", metadataHash = -1044278955)
    }

}
