package org.jetbrains.idea.eclipse.config.impl

import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.metadata.impl.MetadataStorageBase
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.metadata.model.ExtPropertyMetadata
import com.intellij.platform.workspace.storage.metadata.model.ExtendableClassMetadata
import com.intellij.platform.workspace.storage.metadata.model.FinalClassMetadata
import com.intellij.platform.workspace.storage.metadata.model.OwnPropertyMetadata
import com.intellij.platform.workspace.storage.metadata.model.StorageTypeMetadata
import com.intellij.platform.workspace.storage.metadata.model.ValueTypeMetadata

@OptIn(WorkspaceEntityInternalApi::class)
internal object MetadataStorageImpl: MetadataStorageBase() {
    override fun initializeMetadata() {
        val primitiveTypeIntNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "Int")
        val primitiveTypeStringNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "String")
        val primitiveTypeMapNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "Map")
        val primitiveTypeListNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "List")
        val primitiveTypeBooleanNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "Boolean")

        var typeMetadata: StorageTypeMetadata

        typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "org.jetbrains.idea.eclipse.config.EclipseProjectFile", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "classpathFile", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "internalSource", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(fqName = "com.intellij.platform.workspace.jps.JpsFileEntitySource", subclasses = listOf(FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.jps.JpsProjectFileEntitySource\$FileInDirectory", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "directory", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "fileNameId", valueType = primitiveTypeIntNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "projectLocation", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.jps.JpsProjectConfigLocation")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "virtualFileUrl", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.jps.JpsFileEntitySource",
"com.intellij.platform.workspace.jps.JpsProjectFileEntitySource",
"com.intellij.platform.workspace.storage.EntitySource")),
FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.jps.JpsGlobalFileEntitySource", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "file", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "virtualFileUrl", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = true, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.jps.GlobalStorageEntitySource",
"com.intellij.platform.workspace.jps.JpsFileEntitySource",
"com.intellij.platform.workspace.storage.EntitySource")),
FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.jps.JpsProjectFileEntitySource\$ExactFile", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "file", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "projectLocation", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(fqName = "com.intellij.platform.workspace.jps.JpsProjectConfigLocation", subclasses = listOf(FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.jps.JpsProjectConfigLocation\$DirectoryBased", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "baseDirectoryUrl", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "baseDirectoryUrlString", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "ideaFolder", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "projectDir", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "projectFilePath", valueType = primitiveTypeStringNotNullable, withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.jps.JpsProjectConfigLocation")),
FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.jps.JpsProjectConfigLocation\$FileBased", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "baseDirectoryUrl", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "baseDirectoryUrlString", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "iprFile", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "iprFileParent", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "projectFilePath", valueType = primitiveTypeStringNotNullable, withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.jps.JpsProjectConfigLocation"))), supertypes = listOf())), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "virtualFileUrl", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.jps.JpsFileEntitySource",
"com.intellij.platform.workspace.jps.JpsProjectFileEntitySource",
"com.intellij.platform.workspace.storage.EntitySource"))), supertypes = listOf("com.intellij.platform.workspace.storage.EntitySource"))), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "originalSource", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.jps.JpsFileEntitySource")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "projectLocation", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.jps.JpsProjectConfigLocation")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "virtualFileUrl", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = true, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.jps.JpsFileDependentEntitySource",
"com.intellij.platform.workspace.storage.EntitySource"))

        addMetadata(typeMetadata)

        typeMetadata = EntityMetadata(fqName = "org.jetbrains.idea.eclipse.config.EclipseProjectPropertiesEntity", entityDataFqName = "org.jetbrains.idea.eclipse.config.impl.EclipseProjectPropertiesEntityData", supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity"), properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.EntitySource")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "module", valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE, entityFqName = "com.intellij.platform.workspace.jps.entities.ModuleEntity", isChild = false, isNullable = false), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "variablePaths", valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(primitiveTypeStringNotNullable,
primitiveTypeStringNotNullable), primitive = primitiveTypeMapNotNullable), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "eclipseUrls", valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl"))), primitive = primitiveTypeListNotNullable), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "unknownCons", valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(primitiveTypeStringNotNullable), primitive = primitiveTypeListNotNullable), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "knownCons", valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(primitiveTypeStringNotNullable), primitive = primitiveTypeListNotNullable), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "forceConfigureJdk", valueType = primitiveTypeBooleanNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "expectedModuleSourcePlace", valueType = primitiveTypeIntNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "srcPlace", valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(primitiveTypeStringNotNullable,
primitiveTypeIntNotNullable), primitive = primitiveTypeMapNotNullable), withDefault = false)), extProperties = listOf(ExtPropertyMetadata(isComputable = false, isOpen = false, name = "eclipseProperties", receiverFqn = "com.intellij.platform.workspace.jps.entities.ModuleEntity", valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE, entityFqName = "org.jetbrains.idea.eclipse.config.EclipseProjectPropertiesEntity", isChild = true, isNullable = true), withDefault = false)), isAbstract = false)

        addMetadata(typeMetadata)
    }

    override fun initializeMetadataHash() {
        addMetadataHash(typeFqn = "org.jetbrains.idea.eclipse.config.EclipseProjectPropertiesEntity", metadataHash = 1101115316)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.EntitySource", metadataHash = 1581766615)
        addMetadataHash(typeFqn = "org.jetbrains.idea.eclipse.config.EclipseProjectFile", metadataHash = 2034976243)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.JpsFileEntitySource", metadataHash = 1441310205)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.JpsGlobalFileEntitySource", metadataHash = -29934016)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.JpsProjectFileEntitySource", metadataHash = -1393975338)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.JpsProjectFileEntitySource\$ExactFile", metadataHash = -835143991)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.JpsProjectConfigLocation", metadataHash = -1739374703)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.JpsProjectConfigLocation\$DirectoryBased", metadataHash = 1161787715)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.JpsProjectConfigLocation\$FileBased", metadataHash = -2127607065)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.JpsProjectFileEntitySource\$FileInDirectory", metadataHash = -1436516072)
    }

}
