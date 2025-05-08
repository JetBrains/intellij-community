package org.jetbrains.kotlin.idea.workspaceModel.impl

import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.metadata.impl.MetadataStorageBase
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.metadata.model.ExtPropertyMetadata
import com.intellij.platform.workspace.storage.metadata.model.FinalClassMetadata
import com.intellij.platform.workspace.storage.metadata.model.OwnPropertyMetadata
import com.intellij.platform.workspace.storage.metadata.model.StorageTypeMetadata
import com.intellij.platform.workspace.storage.metadata.model.ValueTypeMetadata

@OptIn(WorkspaceEntityInternalApi::class)
internal object MetadataStorageImpl: MetadataStorageBase() {
    override fun initializeMetadata() {
        val primitiveTypeStringNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "String")
        val primitiveTypeListNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "List")
        val primitiveTypeBooleanNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "Boolean")
        val primitiveTypeSetNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "Set")
        val primitiveTypeStringNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = true, type = "String")
        val primitiveTypeIntNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "Int")

        var typeMetadata: StorageTypeMetadata

        typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "org.jetbrains.kotlin.idea.workspaceModel.KotlinSettingsId", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "name", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "parentId", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.jps.entities.ModuleId", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "name", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "presentableName", valueType = primitiveTypeStringNotNullable, withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId"))), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "presentableName", valueType = primitiveTypeStringNotNullable, withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId"))

        addMetadata(typeMetadata)

        typeMetadata = EntityMetadata(fqName = "org.jetbrains.kotlin.idea.workspaceModel.KotlinSettingsEntity", entityDataFqName = "org.jetbrains.kotlin.idea.workspaceModel.impl.KotlinSettingsEntityData", supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity",
"com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId",
"com.intellij.platform.workspace.jps.entities.ModuleSettingsFacetBridgeEntity"), properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.EntitySource")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "moduleId", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.jps.entities.ModuleId", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "name", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "presentableName", valueType = primitiveTypeStringNotNullable, withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId"))), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "name", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "sourceRoots", valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(primitiveTypeStringNotNullable), primitive = primitiveTypeListNotNullable), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "configFileItems", valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "com.intellij.util.descriptors.ConfigFileItem", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "id", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "url", valueType = primitiveTypeStringNotNullable, withDefault = false)), supertypes = listOf()))), primitive = primitiveTypeListNotNullable), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "module", valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY, entityFqName = "com.intellij.platform.workspace.jps.entities.ModuleEntity", isChild = false, isNullable = false), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "useProjectSettings", valueType = primitiveTypeBooleanNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "implementedModuleNames", valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(primitiveTypeStringNotNullable), primitive = primitiveTypeListNotNullable), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "dependsOnModuleNames", valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(primitiveTypeStringNotNullable), primitive = primitiveTypeListNotNullable), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "additionalVisibleModuleNames", valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(primitiveTypeStringNotNullable), primitive = primitiveTypeSetNotNullable), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "productionOutputPath", valueType = primitiveTypeStringNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "testOutputPath", valueType = primitiveTypeStringNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "sourceSetNames", valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(primitiveTypeStringNotNullable), primitive = primitiveTypeListNotNullable), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "isTestModule", valueType = primitiveTypeBooleanNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "externalProjectId", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "isHmppEnabled", valueType = primitiveTypeBooleanNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "pureKotlinSourceFolders", valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(primitiveTypeStringNotNullable), primitive = primitiveTypeListNotNullable), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "kind", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.EnumClassMetadata(fqName = "org.jetbrains.kotlin.config.KotlinModuleKind", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "isNewMPP", valueType = primitiveTypeBooleanNotNullable, withDefault = false)), supertypes = listOf("java.io.Serializable",
"kotlin.Comparable",
"kotlin.Enum"), values = listOf("COMPILATION_AND_SOURCE_SET_HOLDER",
"DEFAULT",
"SOURCE_SET_HOLDER"))), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "compilerArguments", valueType = primitiveTypeStringNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "compilerSettings", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = true, typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "org.jetbrains.kotlin.idea.workspaceModel.CompilerSettingsData", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "additionalArguments", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "copyJsLibraryFiles", valueType = primitiveTypeBooleanNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "outputDirectoryForJsLibraryFiles", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "scriptTemplates", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "scriptTemplatesClasspath", valueType = primitiveTypeStringNotNullable, withDefault = false)), supertypes = listOf())), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "targetPlatform", valueType = primitiveTypeStringNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "externalSystemRunTasks", valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(primitiveTypeStringNotNullable), primitive = primitiveTypeListNotNullable), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "version", valueType = primitiveTypeIntNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "flushNeeded", valueType = primitiveTypeBooleanNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = true, isKey = false, isOpen = false, name = "symbolicId", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "org.jetbrains.kotlin.idea.workspaceModel.KotlinSettingsId", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "name", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "parentId", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.jps.entities.ModuleId", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "name", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "presentableName", valueType = primitiveTypeStringNotNullable, withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId"))), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "presentableName", valueType = primitiveTypeStringNotNullable, withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId"))), withDefault = false)), extProperties = listOf(ExtPropertyMetadata(isComputable = false, isOpen = false, name = "kotlinSettings", receiverFqn = "com.intellij.platform.workspace.jps.entities.ModuleEntity", valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY, entityFqName = "org.jetbrains.kotlin.idea.workspaceModel.KotlinSettingsEntity", isChild = true, isNullable = false), withDefault = false)), isAbstract = false)

        addMetadata(typeMetadata)
    }

    override fun initializeMetadataHash() {
        addMetadataHash(typeFqn = "org.jetbrains.kotlin.idea.workspaceModel.KotlinSettingsEntity", metadataHash = -1158276642)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.entities.ModuleId", metadataHash = -575206713)
        addMetadataHash(typeFqn = "com.intellij.util.descriptors.ConfigFileItem", metadataHash = -445249281)
        addMetadataHash(typeFqn = "org.jetbrains.kotlin.config.KotlinModuleKind", metadataHash = 833411356)
        addMetadataHash(typeFqn = "org.jetbrains.kotlin.idea.workspaceModel.CompilerSettingsData", metadataHash = -1104123232)
        addMetadataHash(typeFqn = "org.jetbrains.kotlin.idea.workspaceModel.KotlinSettingsId", metadataHash = 236435207)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.SymbolicEntityId", metadataHash = -1609236105)
    }

}
