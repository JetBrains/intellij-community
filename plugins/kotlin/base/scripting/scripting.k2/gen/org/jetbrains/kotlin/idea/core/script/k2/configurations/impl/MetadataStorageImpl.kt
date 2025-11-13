// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.configurations.impl

import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.metadata.impl.MetadataStorageBase
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.metadata.model.FinalClassMetadata
import com.intellij.platform.workspace.storage.metadata.model.OwnPropertyMetadata
import com.intellij.platform.workspace.storage.metadata.model.StorageTypeMetadata
import com.intellij.platform.workspace.storage.metadata.model.ValueTypeMetadata

@OptIn(WorkspaceEntityInternalApi::class)
internal object MetadataStorageImpl : MetadataStorageBase() {
  override fun initializeMetadata() {
    val primitiveTypeListNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "List")
    val primitiveTypeStringNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "String")
    val primitiveTypeIntNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "Int")
    val primitiveTypeStringNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = true, type = "String")

    var typeMetadata: StorageTypeMetadata

    typeMetadata = FinalClassMetadata.ObjectMetadata(
      fqName = "org.jetbrains.kotlin.idea.core.script.k2.configurations.MainKtsConfigurationProvider\$MainKtsKotlinScriptEntitySource",
      properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "virtualFileUrl",
                                              valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = true,
                                                                                                  typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                    fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                                              withDefault = false)),
      supertypes = listOf("com.intellij.platform.workspace.storage.EntitySource"))

    addMetadata(typeMetadata)

    typeMetadata = FinalClassMetadata.ObjectMetadata(
      fqName = "org.jetbrains.kotlin.idea.core.script.k2.configurations.DefaultScriptConfigurationHandler\$DefaultScriptEntitySource",
      properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "virtualFileUrl",
                                              valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = true,
                                                                                                  typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                    fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                                              withDefault = false)),
      supertypes = listOf("com.intellij.platform.workspace.storage.EntitySource"))

    addMetadata(typeMetadata)

    typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptLibraryEntityId",
                                                    properties = listOf(
                                                      OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false,
                                                                          name = "classes", valueType = ValueTypeMetadata.ParameterizedType(
                                                          generics = listOf(ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                    typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                      fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl"))),
                                                          primitive = primitiveTypeListNotNullable), withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false,
                                                                          name = "presentableName",
                                                                          valueType = primitiveTypeStringNotNullable, withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false,
                                                                          name = "sources", valueType = ValueTypeMetadata.ParameterizedType(
                                                          generics = listOf(ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                    typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                      fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl"))),
                                                          primitive = primitiveTypeListNotNullable), withDefault = false)),
                                                    supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId"))

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntity",
                                  entityDataFqName = "org.jetbrains.kotlin.idea.core.script.k2.modules.impl.KotlinScriptEntityData",
                                  supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity"), properties = listOf(
        OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource",
                            valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                typeMetadata = FinalClassMetadata.KnownClass(
                                                                                  fqName = "com.intellij.platform.workspace.storage.EntitySource")),
                            withDefault = false),
        OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "virtualFileUrl",
                            valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                typeMetadata = FinalClassMetadata.KnownClass(
                                                                                  fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                            withDefault = false),
        OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "dependencies",
                            valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(
                              ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.ClassMetadata(
                                fqName = "org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptLibraryEntityId",
                                properties = listOf(
                                  OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "classes",
                                                      valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                        ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                  fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl"))),
                                                                                                      primitive = primitiveTypeListNotNullable),
                                                      withDefault = false),
                                  OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "presentableName",
                                                      valueType = primitiveTypeStringNotNullable, withDefault = false),
                                  OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "sources",
                                                      valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                        ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                  fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl"))),
                                                                                                      primitive = primitiveTypeListNotNullable),
                                                      withDefault = false)),
                                supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId")))),
                                                                            primitive = primitiveTypeListNotNullable), withDefault = false),
        OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "configuration",
                            valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = true,
                                                                                typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                  fqName = "org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptCompilationConfigurationEntity",
                                                                                  properties = listOf(
                                                                                    OwnPropertyMetadata(isComputable = false, isKey = false,
                                                                                                        isOpen = false, name = "data",
                                                                                                        valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                                          isNullable = false,
                                                                                                          typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                            fqName = "kotlin.ByteArray")),
                                                                                                        withDefault = false)),
                                                                                  supertypes = listOf())), withDefault = false),
        OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "sdkId",
                            valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = true,
                                                                                typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                  fqName = "com.intellij.platform.workspace.jps.entities.SdkId",
                                                                                  properties = listOf(
                                                                                    OwnPropertyMetadata(isComputable = false, isKey = false,
                                                                                                        isOpen = false, name = "name",
                                                                                                        valueType = primitiveTypeStringNotNullable,
                                                                                                        withDefault = false),
                                                                                    OwnPropertyMetadata(isComputable = false, isKey = false,
                                                                                                        isOpen = false,
                                                                                                        name = "presentableName",
                                                                                                        valueType = primitiveTypeStringNotNullable,
                                                                                                        withDefault = false),
                                                                                    OwnPropertyMetadata(isComputable = false, isKey = false,
                                                                                                        isOpen = false, name = "type",
                                                                                                        valueType = primitiveTypeStringNotNullable,
                                                                                                        withDefault = false)),
                                                                                  supertypes = listOf(
                                                                                    "com.intellij.platform.workspace.storage.SymbolicEntityId"))),
                            withDefault = false),
        OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "reports",
                            valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(
                              ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.ClassMetadata(
                                fqName = "org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptDiagnosticData", properties = listOf(
                                  OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "code",
                                                      valueType = primitiveTypeIntNotNullable, withDefault = false),
                                  OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "exceptionMessage",
                                                      valueType = primitiveTypeStringNullable, withDefault = false),
                                  OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "location",
                                                      valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = true,
                                                                                                          typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                            fqName = "kotlin.script.experimental.api.SourceCode\$Location")),
                                                      withDefault = false),
                                  OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "message",
                                                      valueType = primitiveTypeStringNotNullable, withDefault = false),
                                  OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "severity",
                                                      valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                          typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                            fqName = "kotlin.script.experimental.api.ScriptDiagnostic\$Severity")),
                                                      withDefault = false),
                                  OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "sourcePath",
                                                      valueType = primitiveTypeStringNullable, withDefault = false)),
                                supertypes = listOf()))), primitive = primitiveTypeListNotNullable), withDefault = true)),
                                  extProperties = listOf(), isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptLibraryEntity",
                                  entityDataFqName = "org.jetbrains.kotlin.idea.core.script.k2.modules.impl.KotlinScriptLibraryEntityData",
                                  supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity",
                                                      "com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId"),
                                  properties = listOf(
                                    OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource",
                                                        valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                            typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                              fqName = "com.intellij.platform.workspace.storage.EntitySource")),
                                                        withDefault = false),
                                    OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "classes",
                                                        valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                          ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                  typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                    fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl"))),
                                                                                                        primitive = primitiveTypeListNotNullable),
                                                        withDefault = false),
                                    OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "sources",
                                                        valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                          ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                  typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                    fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl"))),
                                                                                                        primitive = primitiveTypeListNotNullable),
                                                        withDefault = false),
                                    OwnPropertyMetadata(isComputable = true, isKey = false, isOpen = false, name = "symbolicId",
                                                        valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                            typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                              fqName = "org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptLibraryEntityId",
                                                                                                              properties = listOf(
                                                                                                                OwnPropertyMetadata(
                                                                                                                  isComputable = false,
                                                                                                                  isKey = false,
                                                                                                                  isOpen = false,
                                                                                                                  name = "classes",
                                                                                                                  valueType = ValueTypeMetadata.ParameterizedType(
                                                                                                                    generics = listOf(
                                                                                                                      ValueTypeMetadata.SimpleType.CustomType(
                                                                                                                        isNullable = false,
                                                                                                                        typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                          fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl"))),
                                                                                                                    primitive = primitiveTypeListNotNullable),
                                                                                                                  withDefault = false),
                                                                                                                OwnPropertyMetadata(
                                                                                                                  isComputable = false,
                                                                                                                  isKey = false,
                                                                                                                  isOpen = false,
                                                                                                                  name = "presentableName",
                                                                                                                  valueType = primitiveTypeStringNotNullable,
                                                                                                                  withDefault = false),
                                                                                                                OwnPropertyMetadata(
                                                                                                                  isComputable = false,
                                                                                                                  isKey = false,
                                                                                                                  isOpen = false,
                                                                                                                  name = "sources",
                                                                                                                  valueType = ValueTypeMetadata.ParameterizedType(
                                                                                                                    generics = listOf(
                                                                                                                      ValueTypeMetadata.SimpleType.CustomType(
                                                                                                                        isNullable = false,
                                                                                                                        typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                          fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl"))),
                                                                                                                    primitive = primitiveTypeListNotNullable),
                                                                                                                  withDefault = false)),
                                                                                                              supertypes = listOf(
                                                                                                                "com.intellij.platform.workspace.storage.SymbolicEntityId"))),
                                                        withDefault = false)), extProperties = listOf(), isAbstract = false)

    addMetadata(typeMetadata)
  }

  override fun initializeMetadataHash() {
    addMetadataHash(typeFqn = "org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntity", metadataHash = -904523740)
    addMetadataHash(typeFqn = "org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptLibraryEntity", metadataHash = -366522520)
    addMetadataHash(typeFqn = "org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptLibraryEntityId", metadataHash = -1990443645)
    addMetadataHash(typeFqn = "org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptCompilationConfigurationEntity",
                    metadataHash = -1162660984)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.entities.SdkId", metadataHash = 51502100)
    addMetadataHash(typeFqn = "org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptDiagnosticData", metadataHash = -579968699)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.EntitySource", metadataHash = 1965151115)
    addMetadataHash(
      typeFqn = "org.jetbrains.kotlin.idea.core.script.k2.configurations.DefaultScriptConfigurationHandler\$DefaultScriptEntitySource",
      metadataHash = -513260474)
    addMetadataHash(
      typeFqn = "org.jetbrains.kotlin.idea.core.script.k2.configurations.MainKtsConfigurationProvider\$MainKtsKotlinScriptEntitySource",
      metadataHash = 618687241)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.SymbolicEntityId", metadataHash = -2087540107)
  }

}
