// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.workspace.entities

import com.intellij.platform.workspace.storage.impl.ConnectionId
import com.intellij.platform.workspace.storage.metadata.impl.MetadataStorageBase
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.metadata.model.ExtPropertyMetadata
import com.intellij.platform.workspace.storage.metadata.model.ExtendableClassMetadata
import com.intellij.platform.workspace.storage.metadata.model.FinalClassMetadata
import com.intellij.platform.workspace.storage.metadata.model.OwnPropertyMetadata
import com.intellij.platform.workspace.storage.metadata.model.StorageTypeMetadata
import com.intellij.platform.workspace.storage.metadata.model.ValueTypeMetadata

object MetadataStorageImpl: MetadataStorageBase() {
    override fun initializeMetadata() {
        val primitiveTypeStringNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "String")
        val primitiveTypeBooleanNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "Boolean")
        val primitiveTypeStringNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = true, type = "String")
        val primitiveTypeListNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "List")
        val primitiveTypeIntNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "Int")

        var typeMetadata: StorageTypeMetadata

        typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "com.intellij.java.workspace.entities.ArtifactId", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "presentableName", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "name", valueType = primitiveTypeStringNotNullable, withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId"))

        addMetadata(typeMetadata)

        typeMetadata = EntityMetadata(fqName = "com.intellij.java.workspace.entities.ArchivePackagingElementEntity", entityDataFqName = "com.intellij.java.workspace.entities.ArchivePackagingElementEntityData", supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity",
"com.intellij.java.workspace.entities.PackagingElementEntity",
"com.intellij.java.workspace.entities.CompositePackagingElementEntity"), properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.EntitySource")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "parentEntity", valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY, entityFqName = "com.intellij.java.workspace.entities.CompositePackagingElementEntity", isChild = false, isNullable = true), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "artifact", valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE, entityFqName = "com.intellij.java.workspace.entities.ArtifactEntity", isChild = false, isNullable = true), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "children", valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY, entityFqName = "com.intellij.java.workspace.entities.PackagingElementEntity", isChild = true, isNullable = false), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "fileName", valueType = primitiveTypeStringNotNullable, withDefault = false)), extProperties = listOf(), isAbstract = false)

        addMetadata(typeMetadata)

        typeMetadata = EntityMetadata(fqName = "com.intellij.java.workspace.entities.ArtifactEntity", entityDataFqName = "com.intellij.java.workspace.entities.ArtifactEntityData", supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity",
"com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId"), properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.EntitySource")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "name", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "artifactType", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "includeInProjectBuild", valueType = primitiveTypeBooleanNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "outputUrl", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = true, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "rootElement", valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE, entityFqName = "com.intellij.java.workspace.entities.CompositePackagingElementEntity", isChild = true, isNullable = true), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "customProperties", valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY, entityFqName = "com.intellij.java.workspace.entities.ArtifactPropertiesEntity", isChild = true, isNullable = false), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "artifactOutputPackagingElement", valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE, entityFqName = "com.intellij.java.workspace.entities.ArtifactOutputPackagingElementEntity", isChild = true, isNullable = true), withDefault = false),
OwnPropertyMetadata(isComputable = true, isKey = false, isOpen = false, name = "symbolicId", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "com.intellij.java.workspace.entities.ArtifactId", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "presentableName", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "name", valueType = primitiveTypeStringNotNullable, withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId"))), withDefault = false)), extProperties = listOf(ExtPropertyMetadata(isComputable = false, isOpen = false, name = "artifactEntity", receiverFqn = "com.intellij.java.workspace.entities.ArtifactOutputPackagingElementEntity", valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE, entityFqName = "com.intellij.java.workspace.entities.ArtifactEntity", isChild = false, isNullable = false), withDefault = false)), isAbstract = false)

        addMetadata(typeMetadata)

        typeMetadata = EntityMetadata(fqName = "com.intellij.java.workspace.entities.ArtifactOutputPackagingElementEntity", entityDataFqName = "com.intellij.java.workspace.entities.ArtifactOutputPackagingElementEntityData", supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity",
"com.intellij.java.workspace.entities.PackagingElementEntity"), properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.EntitySource")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "parentEntity", valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY, entityFqName = "com.intellij.java.workspace.entities.CompositePackagingElementEntity", isChild = false, isNullable = true), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "artifact", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = true, typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "com.intellij.java.workspace.entities.ArtifactId", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "presentableName", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "name", valueType = primitiveTypeStringNotNullable, withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId"))), withDefault = false)), extProperties = listOf(), isAbstract = false)

        addMetadata(typeMetadata)

        typeMetadata = EntityMetadata(fqName = "com.intellij.java.workspace.entities.ArtifactPropertiesEntity", entityDataFqName = "com.intellij.java.workspace.entities.ArtifactPropertiesEntityData", supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity"), properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.EntitySource")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "artifact", valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY, entityFqName = "com.intellij.java.workspace.entities.ArtifactEntity", isChild = false, isNullable = false), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "providerType", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "propertiesXmlTag", valueType = primitiveTypeStringNullable, withDefault = false)), extProperties = listOf(), isAbstract = false)

        addMetadata(typeMetadata)

        typeMetadata = EntityMetadata(fqName = "com.intellij.java.workspace.entities.ArtifactRootElementEntity", entityDataFqName = "com.intellij.java.workspace.entities.ArtifactRootElementEntityData", supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity",
"com.intellij.java.workspace.entities.PackagingElementEntity",
"com.intellij.java.workspace.entities.CompositePackagingElementEntity"), properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.EntitySource")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "parentEntity", valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY, entityFqName = "com.intellij.java.workspace.entities.CompositePackagingElementEntity", isChild = false, isNullable = true), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "artifact", valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE, entityFqName = "com.intellij.java.workspace.entities.ArtifactEntity", isChild = false, isNullable = true), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "children", valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY, entityFqName = "com.intellij.java.workspace.entities.PackagingElementEntity", isChild = true, isNullable = false), withDefault = false)), extProperties = listOf(), isAbstract = false)

        addMetadata(typeMetadata)

        typeMetadata = EntityMetadata(fqName = "com.intellij.java.workspace.entities.ArtifactsOrderEntity", entityDataFqName = "com.intellij.java.workspace.entities.ArtifactsOrderEntityData", supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity"), properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.EntitySource")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "orderOfArtifacts", valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(primitiveTypeStringNotNullable), primitive = primitiveTypeListNotNullable), withDefault = false)), extProperties = listOf(), isAbstract = false)

        addMetadata(typeMetadata)

        typeMetadata = EntityMetadata(fqName = "com.intellij.java.workspace.entities.CompositePackagingElementEntity", entityDataFqName = "com.intellij.java.workspace.entities.CompositePackagingElementEntityData", supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity",
"com.intellij.java.workspace.entities.PackagingElementEntity"), properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.EntitySource")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "parentEntity", valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY, entityFqName = "com.intellij.java.workspace.entities.CompositePackagingElementEntity", isChild = false, isNullable = true), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "artifact", valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE, entityFqName = "com.intellij.java.workspace.entities.ArtifactEntity", isChild = false, isNullable = true), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "children", valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY, entityFqName = "com.intellij.java.workspace.entities.PackagingElementEntity", isChild = true, isNullable = false), withDefault = false)), extProperties = listOf(), isAbstract = true)

        addMetadata(typeMetadata)

        typeMetadata = EntityMetadata(fqName = "com.intellij.java.workspace.entities.CustomPackagingElementEntity", entityDataFqName = "com.intellij.java.workspace.entities.CustomPackagingElementEntityData", supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity",
"com.intellij.java.workspace.entities.PackagingElementEntity",
"com.intellij.java.workspace.entities.CompositePackagingElementEntity"), properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.EntitySource")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "parentEntity", valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY, entityFqName = "com.intellij.java.workspace.entities.CompositePackagingElementEntity", isChild = false, isNullable = true), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "artifact", valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE, entityFqName = "com.intellij.java.workspace.entities.ArtifactEntity", isChild = false, isNullable = true), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "children", valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY, entityFqName = "com.intellij.java.workspace.entities.PackagingElementEntity", isChild = true, isNullable = false), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "typeId", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "propertiesXmlTag", valueType = primitiveTypeStringNotNullable, withDefault = false)), extProperties = listOf(), isAbstract = false)

        addMetadata(typeMetadata)

        typeMetadata = EntityMetadata(fqName = "com.intellij.java.workspace.entities.DirectoryCopyPackagingElementEntity", entityDataFqName = "com.intellij.java.workspace.entities.DirectoryCopyPackagingElementEntityData", supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity",
"com.intellij.java.workspace.entities.PackagingElementEntity",
"com.intellij.java.workspace.entities.FileOrDirectoryPackagingElementEntity"), properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.EntitySource")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "parentEntity", valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY, entityFqName = "com.intellij.java.workspace.entities.CompositePackagingElementEntity", isChild = false, isNullable = true), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "filePath", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false)), extProperties = listOf(), isAbstract = false)

        addMetadata(typeMetadata)

        typeMetadata = EntityMetadata(fqName = "com.intellij.java.workspace.entities.DirectoryPackagingElementEntity", entityDataFqName = "com.intellij.java.workspace.entities.DirectoryPackagingElementEntityData", supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity",
"com.intellij.java.workspace.entities.PackagingElementEntity",
"com.intellij.java.workspace.entities.CompositePackagingElementEntity"), properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.EntitySource")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "parentEntity", valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY, entityFqName = "com.intellij.java.workspace.entities.CompositePackagingElementEntity", isChild = false, isNullable = true), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "artifact", valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE, entityFqName = "com.intellij.java.workspace.entities.ArtifactEntity", isChild = false, isNullable = true), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "children", valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY, entityFqName = "com.intellij.java.workspace.entities.PackagingElementEntity", isChild = true, isNullable = false), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "directoryName", valueType = primitiveTypeStringNotNullable, withDefault = false)), extProperties = listOf(), isAbstract = false)

        addMetadata(typeMetadata)

        typeMetadata = EntityMetadata(fqName = "com.intellij.java.workspace.entities.ExtractedDirectoryPackagingElementEntity", entityDataFqName = "com.intellij.java.workspace.entities.ExtractedDirectoryPackagingElementEntityData", supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity",
"com.intellij.java.workspace.entities.PackagingElementEntity",
"com.intellij.java.workspace.entities.FileOrDirectoryPackagingElementEntity"), properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.EntitySource")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "parentEntity", valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY, entityFqName = "com.intellij.java.workspace.entities.CompositePackagingElementEntity", isChild = false, isNullable = true), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "filePath", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "pathInArchive", valueType = primitiveTypeStringNotNullable, withDefault = false)), extProperties = listOf(), isAbstract = false)

        addMetadata(typeMetadata)

        typeMetadata = EntityMetadata(fqName = "com.intellij.java.workspace.entities.FileCopyPackagingElementEntity", entityDataFqName = "com.intellij.java.workspace.entities.FileCopyPackagingElementEntityData", supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity",
"com.intellij.java.workspace.entities.PackagingElementEntity",
"com.intellij.java.workspace.entities.FileOrDirectoryPackagingElementEntity"), properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.EntitySource")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "parentEntity", valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY, entityFqName = "com.intellij.java.workspace.entities.CompositePackagingElementEntity", isChild = false, isNullable = true), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "filePath", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "renamedOutputFileName", valueType = primitiveTypeStringNullable, withDefault = false)), extProperties = listOf(), isAbstract = false)

        addMetadata(typeMetadata)

        typeMetadata = EntityMetadata(fqName = "com.intellij.java.workspace.entities.FileOrDirectoryPackagingElementEntity", entityDataFqName = "com.intellij.java.workspace.entities.FileOrDirectoryPackagingElementEntityData", supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity",
"com.intellij.java.workspace.entities.PackagingElementEntity"), properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.EntitySource")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "parentEntity", valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY, entityFqName = "com.intellij.java.workspace.entities.CompositePackagingElementEntity", isChild = false, isNullable = true), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "filePath", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false)), extProperties = listOf(), isAbstract = true)

        addMetadata(typeMetadata)

        typeMetadata = EntityMetadata(fqName = "com.intellij.java.workspace.entities.JavaModuleSettingsEntity", entityDataFqName = "com.intellij.java.workspace.entities.JavaModuleSettingsEntityData", supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity"), properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.EntitySource")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "module", valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE, entityFqName = "com.intellij.platform.workspace.jps.entities.ModuleEntity", isChild = false, isNullable = false), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "inheritedCompilerOutput", valueType = primitiveTypeBooleanNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "excludeOutput", valueType = primitiveTypeBooleanNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "compilerOutput", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = true, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "compilerOutputForTests", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = true, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "languageLevelId", valueType = primitiveTypeStringNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "automaticModuleName", valueType = primitiveTypeStringNullable, withDefault = false)), extProperties = listOf(ExtPropertyMetadata(isComputable = false, isOpen = false, name = "javaSettings", receiverFqn = "com.intellij.platform.workspace.jps.entities.ModuleEntity", valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE, entityFqName = "com.intellij.java.workspace.entities.JavaModuleSettingsEntity", isChild = true, isNullable = true), withDefault = false)), isAbstract = false)

        addMetadata(typeMetadata)

        typeMetadata = EntityMetadata(fqName = "com.intellij.java.workspace.entities.JavaResourceRootPropertiesEntity", entityDataFqName = "com.intellij.java.workspace.entities.JavaResourceRootPropertiesEntityData", supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity"), properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.EntitySource")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "sourceRoot", valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY, entityFqName = "com.intellij.platform.workspace.jps.entities.SourceRootEntity", isChild = false, isNullable = false), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "generated", valueType = primitiveTypeBooleanNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "relativeOutputPath", valueType = primitiveTypeStringNotNullable, withDefault = false)), extProperties = listOf(ExtPropertyMetadata(isComputable = false, isOpen = false, name = "javaResourceRoots", receiverFqn = "com.intellij.platform.workspace.jps.entities.SourceRootEntity", valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY, entityFqName = "com.intellij.java.workspace.entities.JavaResourceRootPropertiesEntity", isChild = true, isNullable = false), withDefault = false)), isAbstract = false)

        addMetadata(typeMetadata)

        typeMetadata = EntityMetadata(fqName = "com.intellij.java.workspace.entities.JavaSourceRootPropertiesEntity", entityDataFqName = "com.intellij.java.workspace.entities.JavaSourceRootPropertiesEntityData", supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity"), properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.EntitySource")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "sourceRoot", valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY, entityFqName = "com.intellij.platform.workspace.jps.entities.SourceRootEntity", isChild = false, isNullable = false), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "generated", valueType = primitiveTypeBooleanNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "packagePrefix", valueType = primitiveTypeStringNotNullable, withDefault = false)), extProperties = listOf(ExtPropertyMetadata(isComputable = false, isOpen = false, name = "javaSourceRoots", receiverFqn = "com.intellij.platform.workspace.jps.entities.SourceRootEntity", valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY, entityFqName = "com.intellij.java.workspace.entities.JavaSourceRootPropertiesEntity", isChild = true, isNullable = false), withDefault = false)), isAbstract = false)

        addMetadata(typeMetadata)

        typeMetadata = EntityMetadata(fqName = "com.intellij.java.workspace.entities.LibraryFilesPackagingElementEntity", entityDataFqName = "com.intellij.java.workspace.entities.LibraryFilesPackagingElementEntityData", supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity",
"com.intellij.java.workspace.entities.PackagingElementEntity"), properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.EntitySource")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "parentEntity", valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY, entityFqName = "com.intellij.java.workspace.entities.CompositePackagingElementEntity", isChild = false, isNullable = true), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "library", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = true, typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.jps.entities.LibraryId", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "presentableName", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "codeCache", valueType = primitiveTypeIntNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "name", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "tableId", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(fqName = "com.intellij.platform.workspace.jps.entities.LibraryTableId", subclasses = listOf(FinalClassMetadata.ObjectMetadata(fqName = "com.intellij.platform.workspace.jps.entities.LibraryTableId\$ProjectLibraryTableId", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "level", valueType = primitiveTypeStringNotNullable, withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.jps.entities.LibraryTableId",
"java.io.Serializable")),
FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.jps.entities.LibraryTableId\$GlobalLibraryTableId", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "level", valueType = primitiveTypeStringNotNullable, withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.jps.entities.LibraryTableId",
"java.io.Serializable")),
FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.jps.entities.LibraryTableId\$ModuleLibraryTableId", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "level", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "moduleId", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.jps.entities.ModuleId", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "presentableName", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "name", valueType = primitiveTypeStringNotNullable, withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId"))), withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.jps.entities.LibraryTableId",
"java.io.Serializable"))), supertypes = listOf("java.io.Serializable"))), withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId"))), withDefault = false)), extProperties = listOf(), isAbstract = false)

        addMetadata(typeMetadata)

        typeMetadata = EntityMetadata(fqName = "com.intellij.java.workspace.entities.ModuleOutputPackagingElementEntity", entityDataFqName = "com.intellij.java.workspace.entities.ModuleOutputPackagingElementEntityData", supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity",
"com.intellij.java.workspace.entities.PackagingElementEntity"), properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.EntitySource")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "parentEntity", valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY, entityFqName = "com.intellij.java.workspace.entities.CompositePackagingElementEntity", isChild = false, isNullable = true), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "module", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = true, typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.jps.entities.ModuleId", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "presentableName", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "name", valueType = primitiveTypeStringNotNullable, withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId"))), withDefault = false)), extProperties = listOf(), isAbstract = false)

        addMetadata(typeMetadata)

        typeMetadata = EntityMetadata(fqName = "com.intellij.java.workspace.entities.ModuleSourcePackagingElementEntity", entityDataFqName = "com.intellij.java.workspace.entities.ModuleSourcePackagingElementEntityData", supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity",
"com.intellij.java.workspace.entities.PackagingElementEntity"), properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.EntitySource")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "parentEntity", valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY, entityFqName = "com.intellij.java.workspace.entities.CompositePackagingElementEntity", isChild = false, isNullable = true), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "module", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = true, typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.jps.entities.ModuleId", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "presentableName", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "name", valueType = primitiveTypeStringNotNullable, withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId"))), withDefault = false)), extProperties = listOf(), isAbstract = false)

        addMetadata(typeMetadata)

        typeMetadata = EntityMetadata(fqName = "com.intellij.java.workspace.entities.ModuleTestOutputPackagingElementEntity", entityDataFqName = "com.intellij.java.workspace.entities.ModuleTestOutputPackagingElementEntityData", supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity",
"com.intellij.java.workspace.entities.PackagingElementEntity"), properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.EntitySource")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "parentEntity", valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY, entityFqName = "com.intellij.java.workspace.entities.CompositePackagingElementEntity", isChild = false, isNullable = true), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "module", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = true, typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.jps.entities.ModuleId", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "presentableName", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "name", valueType = primitiveTypeStringNotNullable, withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId"))), withDefault = false)), extProperties = listOf(), isAbstract = false)

        addMetadata(typeMetadata)

        typeMetadata = EntityMetadata(fqName = "com.intellij.java.workspace.entities.PackagingElementEntity", entityDataFqName = "com.intellij.java.workspace.entities.PackagingElementEntityData", supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity"), properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.EntitySource")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "parentEntity", valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY, entityFqName = "com.intellij.java.workspace.entities.CompositePackagingElementEntity", isChild = false, isNullable = true), withDefault = false)), extProperties = listOf(), isAbstract = true)

        addMetadata(typeMetadata)
    }

    override fun initializeMetadataHash() {
        addMetadataHash(typeFqn = "com.intellij.java.workspace.entities.ArchivePackagingElementEntity", metadataHash = 443950773)
        addMetadataHash(typeFqn = "com.intellij.java.workspace.entities.ArtifactEntity", metadataHash = 1722551679)
        addMetadataHash(typeFqn = "com.intellij.java.workspace.entities.ArtifactOutputPackagingElementEntity", metadataHash = -244120967)
        addMetadataHash(typeFqn = "com.intellij.java.workspace.entities.ArtifactPropertiesEntity", metadataHash = -1147891812)
        addMetadataHash(typeFqn = "com.intellij.java.workspace.entities.ArtifactRootElementEntity", metadataHash = -687139657)
        addMetadataHash(typeFqn = "com.intellij.java.workspace.entities.ArtifactsOrderEntity", metadataHash = -552232158)
        addMetadataHash(typeFqn = "com.intellij.java.workspace.entities.CompositePackagingElementEntity", metadataHash = -111136227)
        addMetadataHash(typeFqn = "com.intellij.java.workspace.entities.CustomPackagingElementEntity", metadataHash = 1813828580)
        addMetadataHash(typeFqn = "com.intellij.java.workspace.entities.DirectoryCopyPackagingElementEntity", metadataHash = -1620919365)
        addMetadataHash(typeFqn = "com.intellij.java.workspace.entities.DirectoryPackagingElementEntity", metadataHash = -781019554)
        addMetadataHash(typeFqn = "com.intellij.java.workspace.entities.ExtractedDirectoryPackagingElementEntity", metadataHash = -1256703044)
        addMetadataHash(typeFqn = "com.intellij.java.workspace.entities.FileCopyPackagingElementEntity", metadataHash = -393096503)
        addMetadataHash(typeFqn = "com.intellij.java.workspace.entities.FileOrDirectoryPackagingElementEntity", metadataHash = -2138533204)
        addMetadataHash(typeFqn = "com.intellij.java.workspace.entities.JavaModuleSettingsEntity", metadataHash = 276226566)
        addMetadataHash(typeFqn = "com.intellij.java.workspace.entities.JavaResourceRootPropertiesEntity", metadataHash = -1460280104)
        addMetadataHash(typeFqn = "com.intellij.java.workspace.entities.JavaSourceRootPropertiesEntity", metadataHash = 242650638)
        addMetadataHash(typeFqn = "com.intellij.java.workspace.entities.LibraryFilesPackagingElementEntity", metadataHash = 643862469)
        addMetadataHash(typeFqn = "com.intellij.java.workspace.entities.ModuleOutputPackagingElementEntity", metadataHash = -1242150903)
        addMetadataHash(typeFqn = "com.intellij.java.workspace.entities.ModuleSourcePackagingElementEntity", metadataHash = 1019859157)
        addMetadataHash(typeFqn = "com.intellij.java.workspace.entities.ModuleTestOutputPackagingElementEntity", metadataHash = -249035739)
        addMetadataHash(typeFqn = "com.intellij.java.workspace.entities.PackagingElementEntity", metadataHash = -198043782)
        addMetadataHash(typeFqn = "com.intellij.java.workspace.entities.ArtifactId", metadataHash = 411477007)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.entities.LibraryId", metadataHash = -313921070)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.entities.LibraryTableId", metadataHash = -1475000117)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.entities.LibraryTableId\$GlobalLibraryTableId", metadataHash = 1911253865)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.entities.LibraryTableId\$ModuleLibraryTableId", metadataHash = -1817822292)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.entities.ModuleId", metadataHash = -684863835)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.entities.LibraryTableId\$ProjectLibraryTableId", metadataHash = -1574432194)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.SymbolicEntityId", metadataHash = 2025727337)
    }

}
