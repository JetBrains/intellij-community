package org.jetbrains.kotlin.idea.core.script.impl

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
        val primitiveTypeStringNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "String")
        val primitiveTypeSetNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "Set")
        val primitiveTypeListNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "List")
        val primitiveTypeBooleanNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "Boolean")

        var typeMetadata: StorageTypeMetadata

        typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "org.jetbrains.kotlin.idea.core.script.k2.DefaultScriptConfigurationHandler\$DefaultScriptEntitySource", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "virtualFileUrl", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = true, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.storage.EntitySource",
"org.jetbrains.kotlin.idea.core.script.KotlinScriptEntitySource"))

        addMetadata(typeMetadata)

        typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "org.jetbrains.kotlin.idea.core.script.k2.MainKtsScriptConfigurationProvider\$MainKtsKotlinScriptEntitySource", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "virtualFileUrl", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = true, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.storage.EntitySource",
"org.jetbrains.kotlin.idea.core.script.KotlinScriptEntitySource"))

        addMetadata(typeMetadata)

        typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "org.jetbrains.kotlin.idea.core.script.ucache.KotlinScriptLibraryEntitySource", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "virtualFileUrl", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = true, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.storage.EntitySource"))

        addMetadata(typeMetadata)

        typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "org.jetbrains.kotlin.idea.core.script.ucache.KotlinScriptEntitySource", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "virtualFileUrl", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = true, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.storage.EntitySource"))

        addMetadata(typeMetadata)

        typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "org.jetbrains.kotlin.idea.core.script.ucache.KotlinScriptId", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "path", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "presentableName", valueType = primitiveTypeStringNotNullable, withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId"))

        addMetadata(typeMetadata)

        typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "org.jetbrains.kotlin.idea.core.script.ucache.KotlinScriptLibraryId", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "name", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "presentableName", valueType = primitiveTypeStringNotNullable, withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId"))

        addMetadata(typeMetadata)

        typeMetadata = EntityMetadata(fqName = "org.jetbrains.kotlin.idea.core.script.ucache.KotlinScriptEntity", entityDataFqName = "org.jetbrains.kotlin.idea.core.script.ucache.impl.KotlinScriptEntityData", supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity",
"com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId"), properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.EntitySource")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "path", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "dependencies", valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "org.jetbrains.kotlin.idea.core.script.ucache.KotlinScriptLibraryId", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "name", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "presentableName", valueType = primitiveTypeStringNotNullable, withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId")))), primitive = primitiveTypeSetNotNullable), withDefault = false),
OwnPropertyMetadata(isComputable = true, isKey = false, isOpen = false, name = "symbolicId", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "org.jetbrains.kotlin.idea.core.script.ucache.KotlinScriptId", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "path", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "presentableName", valueType = primitiveTypeStringNotNullable, withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId"))), withDefault = false)), extProperties = listOf(), isAbstract = false)

        addMetadata(typeMetadata)

        typeMetadata = EntityMetadata(fqName = "org.jetbrains.kotlin.idea.core.script.ucache.KotlinScriptLibraryEntity", entityDataFqName = "org.jetbrains.kotlin.idea.core.script.ucache.impl.KotlinScriptLibraryEntityData", supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity",
"com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId"), properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.EntitySource")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "name", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "roots", valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "org.jetbrains.kotlin.idea.core.script.ucache.KotlinScriptLibraryRoot", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "type", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "org.jetbrains.kotlin.idea.core.script.ucache.KotlinScriptLibraryRootTypeId", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "name", valueType = primitiveTypeStringNotNullable, withDefault = false)), supertypes = listOf("java.io.Serializable"))), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "url", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false)), supertypes = listOf("java.io.Serializable")))), primitive = primitiveTypeListNotNullable), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "indexSourceRoots", valueType = primitiveTypeBooleanNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "usedInScripts", valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "org.jetbrains.kotlin.idea.core.script.ucache.KotlinScriptId", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "path", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "presentableName", valueType = primitiveTypeStringNotNullable, withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId")))), primitive = primitiveTypeSetNotNullable), withDefault = false),
OwnPropertyMetadata(isComputable = true, isKey = false, isOpen = false, name = "symbolicId", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "org.jetbrains.kotlin.idea.core.script.ucache.KotlinScriptLibraryId", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "name", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "presentableName", valueType = primitiveTypeStringNotNullable, withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId"))), withDefault = false)), extProperties = listOf(), isAbstract = false)

        addMetadata(typeMetadata)
    }

    override fun initializeMetadataHash() {
        addMetadataHash(typeFqn = "org.jetbrains.kotlin.idea.core.script.ucache.KotlinScriptEntity", metadataHash = 187402499)
        addMetadataHash(typeFqn = "org.jetbrains.kotlin.idea.core.script.ucache.KotlinScriptLibraryEntity", metadataHash = -1434664930)
        addMetadataHash(typeFqn = "org.jetbrains.kotlin.idea.core.script.ucache.KotlinScriptLibraryId", metadataHash = -623708217)
        addMetadataHash(typeFqn = "org.jetbrains.kotlin.idea.core.script.ucache.KotlinScriptId", metadataHash = 1048767682)
        addMetadataHash(typeFqn = "org.jetbrains.kotlin.idea.core.script.ucache.KotlinScriptLibraryRoot", metadataHash = 509227373)
        addMetadataHash(typeFqn = "org.jetbrains.kotlin.idea.core.script.ucache.KotlinScriptLibraryRootTypeId", metadataHash = -2068549764)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.EntitySource", metadataHash = 1946578919)
        addMetadataHash(typeFqn = "org.jetbrains.kotlin.idea.core.script.KotlinScriptEntitySource", metadataHash = -998651648)
        addMetadataHash(typeFqn = "org.jetbrains.kotlin.idea.core.script.k2.DefaultScriptConfigurationHandler\$DefaultScriptEntitySource", metadataHash = -269097726)
        addMetadataHash(typeFqn = "org.jetbrains.kotlin.idea.core.script.k2.MainKtsScriptConfigurationProvider\$MainKtsKotlinScriptEntitySource", metadataHash = 1024193770)
        addMetadataHash(typeFqn = "org.jetbrains.kotlin.idea.core.script.ucache.KotlinScriptEntitySource", metadataHash = 236614476)
        addMetadataHash(typeFqn = "org.jetbrains.kotlin.idea.core.script.ucache.KotlinScriptLibraryEntitySource", metadataHash = 1353548779)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.SymbolicEntityId", metadataHash = -1791994671)
    }

}
