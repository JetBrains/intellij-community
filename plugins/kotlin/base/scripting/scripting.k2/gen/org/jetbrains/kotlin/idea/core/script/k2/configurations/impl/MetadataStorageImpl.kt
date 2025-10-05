// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.configurations.impl

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
        val primitiveTypeListNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "List")
        val primitiveTypeStringNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "String")
        val primitiveTypeBooleanNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "Boolean")
        val primitiveTypeIntNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "Int")
        
        var typeMetadata: StorageTypeMetadata
        
        typeMetadata = FinalClassMetadata.ObjectMetadata(fqName = "org.jetbrains.kotlin.idea.core.script.k2.configurations.MainKtsScriptConfigurationProvider\$MainKtsKotlinScriptEntitySource", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "virtualFileUrl", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = true, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.storage.EntitySource"))
        
        addMetadata(typeMetadata)
        
        typeMetadata = FinalClassMetadata.ObjectMetadata(fqName = "org.jetbrains.kotlin.idea.core.script.k2.configurations.DefaultScriptConfigurationHandler\$DefaultScriptEntitySource", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "virtualFileUrl", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = true, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.storage.EntitySource"))
        
        addMetadata(typeMetadata)
        
        typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptLibraryEntityId", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "classes", valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl"))), primitive = primitiveTypeListNotNullable), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "presentableName", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "sources", valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl"))), primitive = primitiveTypeListNotNullable), withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId"))
        
        addMetadata(typeMetadata)
        
        typeMetadata = EntityMetadata(fqName = "org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntity", entityDataFqName = "org.jetbrains.kotlin.idea.core.script.k2.modules.impl.KotlinScriptEntityData", supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity"), properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.EntitySource")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "virtualFileUrl", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "dependencies", valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptLibraryEntityId", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "classes", valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl"))), primitive = primitiveTypeListNotNullable), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "presentableName", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "sources", valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl"))), primitive = primitiveTypeListNotNullable), withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId")))), primitive = primitiveTypeListNotNullable), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "sdk", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(fqName = "com.intellij.platform.workspace.jps.entities.ModuleDependencyItem", subclasses = listOf(FinalClassMetadata.ObjectMetadata(fqName = "com.intellij.platform.workspace.jps.entities.ModuleSourceDependency", properties = listOf(), supertypes = listOf("com.intellij.platform.workspace.jps.entities.ModuleDependencyItem",
"java.io.Serializable")),
FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.jps.entities.SdkDependency", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "sdk", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.jps.entities.SdkId", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "name", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "presentableName", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "type", valueType = primitiveTypeStringNotNullable, withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId"))), withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.jps.entities.ModuleDependencyItem",
"java.io.Serializable")),
FinalClassMetadata.ObjectMetadata(fqName = "com.intellij.platform.workspace.jps.entities.InheritedSdkDependency", properties = listOf(), supertypes = listOf("com.intellij.platform.workspace.jps.entities.ModuleDependencyItem",
"java.io.Serializable")),
FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.jps.entities.LibraryDependency", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "exported", valueType = primitiveTypeBooleanNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "library", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.jps.entities.LibraryId", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "codeCache", valueType = primitiveTypeIntNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "name", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "presentableName", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "tableId", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(fqName = "com.intellij.platform.workspace.jps.entities.LibraryTableId", subclasses = listOf(FinalClassMetadata.ObjectMetadata(fqName = "com.intellij.platform.workspace.jps.entities.LibraryTableId\$ProjectLibraryTableId", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "level", valueType = primitiveTypeStringNotNullable, withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.jps.entities.LibraryTableId",
"java.io.Serializable")),
FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.jps.entities.LibraryTableId\$GlobalLibraryTableId", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "level", valueType = primitiveTypeStringNotNullable, withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.jps.entities.LibraryTableId",
"java.io.Serializable")),
FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.jps.entities.LibraryTableId\$ModuleLibraryTableId", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "level", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "moduleId", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.jps.entities.ModuleId", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "name", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "presentableName", valueType = primitiveTypeStringNotNullable, withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId"))), withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.jps.entities.LibraryTableId",
"java.io.Serializable"))), supertypes = listOf("java.io.Serializable"))), withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId"))), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "scope", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.EnumClassMetadata(fqName = "com.intellij.platform.workspace.jps.entities.DependencyScope", properties = listOf(), supertypes = listOf("java.io.Serializable",
"kotlin.Comparable",
"kotlin.Enum"), values = listOf("COMPILE",
"PROVIDED",
"RUNTIME",
"TEST"))), withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.jps.entities.ModuleDependencyItem",
"java.io.Serializable")),
FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.jps.entities.ModuleDependency", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "exported", valueType = primitiveTypeBooleanNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "module", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.jps.entities.ModuleId")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "productionOnTest", valueType = primitiveTypeBooleanNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "scope", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.jps.entities.DependencyScope")), withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.jps.entities.ModuleDependencyItem",
"java.io.Serializable"))), supertypes = listOf("java.io.Serializable"))), withDefault = false)), extProperties = listOf(), isAbstract = false)
        
        addMetadata(typeMetadata)
        
        typeMetadata = EntityMetadata(fqName = "org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptLibraryEntity", entityDataFqName = "org.jetbrains.kotlin.idea.core.script.k2.modules.impl.KotlinScriptLibraryEntityData", supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity",
"com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId"), properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.EntitySource")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "classes", valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl"))), primitive = primitiveTypeListNotNullable), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "sources", valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl"))), primitive = primitiveTypeListNotNullable), withDefault = false),
OwnPropertyMetadata(isComputable = true, isKey = false, isOpen = false, name = "symbolicId", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptLibraryEntityId", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "classes", valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl"))), primitive = primitiveTypeListNotNullable), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "presentableName", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "sources", valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl"))), primitive = primitiveTypeListNotNullable), withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId"))), withDefault = false)), extProperties = listOf(), isAbstract = false)
        
        addMetadata(typeMetadata)
    }

    override fun initializeMetadataHash() {
        addMetadataHash(typeFqn = "org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntity", metadataHash = 56160960)
        addMetadataHash(typeFqn = "org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptLibraryEntity", metadataHash = -366522520)
        addMetadataHash(typeFqn = "org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptLibraryEntityId", metadataHash = -1990443645)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.entities.ModuleDependencyItem", metadataHash = -907000292)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.entities.InheritedSdkDependency", metadataHash = -1690206127)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.entities.LibraryDependency", metadataHash = 494611792)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.entities.LibraryId", metadataHash = 1192700660)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.entities.LibraryTableId", metadataHash = -219154451)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.entities.LibraryTableId\$GlobalLibraryTableId", metadataHash = 1911253865)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.entities.LibraryTableId\$ModuleLibraryTableId", metadataHash = 777479370)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.entities.ModuleId", metadataHash = -575206713)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.entities.LibraryTableId\$ProjectLibraryTableId", metadataHash = -1574432194)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.entities.DependencyScope", metadataHash = 1284754485)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.entities.ModuleDependency", metadataHash = -409169331)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.entities.ModuleSourceDependency", metadataHash = -1334065142)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.entities.SdkDependency", metadataHash = 1386665092)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.entities.SdkId", metadataHash = 51502100)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.EntitySource", metadataHash = -706084288)
        addMetadataHash(typeFqn = "org.jetbrains.kotlin.idea.core.script.k2.configurations.DefaultScriptConfigurationHandler\$DefaultScriptEntitySource", metadataHash = -513260474)
        addMetadataHash(typeFqn = "org.jetbrains.kotlin.idea.core.script.k2.configurations.MainKtsScriptConfigurationProvider\$MainKtsKotlinScriptEntitySource", metadataHash = 2056756862)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.SymbolicEntityId", metadataHash = -2087540107)
    }

}
