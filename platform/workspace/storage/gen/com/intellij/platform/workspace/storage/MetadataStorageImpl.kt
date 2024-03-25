// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage

import com.intellij.platform.workspace.storage.metadata.impl.MetadataStorageBase
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.metadata.model.ExtendableClassMetadata
import com.intellij.platform.workspace.storage.metadata.model.FinalClassMetadata
import com.intellij.platform.workspace.storage.metadata.model.OwnPropertyMetadata
import com.intellij.platform.workspace.storage.metadata.model.StorageTypeMetadata
import com.intellij.platform.workspace.storage.metadata.model.ValueTypeMetadata

public object MetadataStorageImpl: MetadataStorageBase() {
    override fun initializeMetadata() {
        val primitiveTypeIntNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "Int")
        val primitiveTypeStringNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "String")
        
        var typeMetadata: StorageTypeMetadata
        
        typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "com.intellij.java.configurationStore.SampleDummyParentCustomModuleEntitySource", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "internalSource", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(fqName = "com.intellij.platform.workspace.jps.JpsFileEntitySource", subclasses = listOf(FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.jps.JpsProjectFileEntitySource\$FileInDirectory", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "fileNameId", valueType = primitiveTypeIntNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "virtualFileUrl", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "directory", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "projectLocation", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.jps.JpsProjectConfigLocation")), withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.jps.JpsProjectFileEntitySource",
"com.intellij.platform.workspace.jps.JpsFileEntitySource",
"com.intellij.platform.workspace.storage.EntitySource")),
FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.jps.JpsGlobalFileEntitySource", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "file", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "virtualFileUrl", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = true, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.jps.JpsFileEntitySource",
"com.intellij.platform.workspace.storage.EntitySource")),
FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.jps.JpsProjectFileEntitySource\$ExactFile", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "virtualFileUrl", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "file", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "projectLocation", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(fqName = "com.intellij.platform.workspace.jps.JpsProjectConfigLocation", subclasses = listOf(FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.jps.JpsProjectConfigLocation\$DirectoryBased", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "baseDirectoryUrl", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "projectFilePath", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "projectDir", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "ideaFolder", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "baseDirectoryUrlString", valueType = primitiveTypeStringNotNullable, withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.jps.JpsProjectConfigLocation")),
FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.jps.JpsProjectConfigLocation\$FileBased", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "baseDirectoryUrl", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "projectFilePath", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "iprFile", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "iprFileParent", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "baseDirectoryUrlString", valueType = primitiveTypeStringNotNullable, withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.jps.JpsProjectConfigLocation"))), supertypes = listOf())), withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.jps.JpsProjectFileEntitySource",
"com.intellij.platform.workspace.jps.JpsFileEntitySource",
"com.intellij.platform.workspace.storage.EntitySource"))), supertypes = listOf("com.intellij.platform.workspace.storage.EntitySource"))), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "virtualFileUrl", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = true, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.jps.CustomModuleEntitySource",
"com.intellij.platform.workspace.storage.DummyParentEntitySource",
"com.intellij.platform.workspace.storage.EntitySource"))
        
        addMetadata(typeMetadata)
        
        typeMetadata = FinalClassMetadata.ObjectMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.MyDummyParentSource", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "virtualFileUrl", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = true, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.storage.DummyParentEntitySource",
"com.intellij.platform.workspace.storage.EntitySource"))
        
        addMetadata(typeMetadata)
        
        typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "com.jetbrains.cidr.workspaceModelBridge.OCEntityDummyModuleSource", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "internalSource", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.jps.JpsFileEntitySource")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "virtualFileUrl", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = true, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false)), supertypes = listOf("com.jetbrains.cidr.workspaceModelBridge.OCEntitySource",
"com.intellij.platform.workspace.storage.DummyParentEntitySource",
"com.intellij.platform.workspace.jps.CustomModuleEntitySource",
"com.intellij.platform.workspace.storage.EntitySource"))
        
        addMetadata(typeMetadata)
        
        typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.WorkspaceEntity", entityDataFqName = "com.intellij.platform.workspace.storage.WorkspaceEntityData", supertypes = listOf(), properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.EntitySource")), withDefault = false)), extProperties = listOf(), isAbstract = true)
        
        addMetadata(typeMetadata)
        
        typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId", entityDataFqName = "com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicIdData", supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity"), properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.EntitySource")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "symbolicId", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.SymbolicEntityId")), withDefault = false)), extProperties = listOf(), isAbstract = true)
        
        addMetadata(typeMetadata)
    }

    override fun initializeMetadataHash() {
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.WorkspaceEntity", metadataHash = -1093343975)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId", metadataHash = 429186493)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.EntitySource", metadataHash = 310149856)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.DummyParentEntitySource", metadataHash = -1374988455)
        addMetadataHash(typeFqn = "com.intellij.java.configurationStore.SampleDummyParentCustomModuleEntitySource", metadataHash = -1785529430)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.JpsFileEntitySource", metadataHash = -1709137196)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.JpsGlobalFileEntitySource", metadataHash = -1063074881)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.JpsProjectFileEntitySource", metadataHash = -182040200)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.JpsProjectFileEntitySource\$ExactFile", metadataHash = 1636464627)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.JpsProjectConfigLocation", metadataHash = 153096835)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.JpsProjectConfigLocation\$DirectoryBased", metadataHash = -1619672141)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.JpsProjectConfigLocation\$FileBased", metadataHash = 656180261)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.JpsProjectFileEntitySource\$FileInDirectory", metadataHash = -292490736)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.MyDummyParentSource", metadataHash = -1458866609)
        addMetadataHash(typeFqn = "com.jetbrains.cidr.workspaceModelBridge.OCEntityDummyModuleSource", metadataHash = 1532878769)
    }

}
