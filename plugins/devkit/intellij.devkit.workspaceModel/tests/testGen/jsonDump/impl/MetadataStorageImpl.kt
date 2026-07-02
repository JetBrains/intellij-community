// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel.jsonDump.impl

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
internal object MetadataStorageImpl : MetadataStorageBase() {
  override fun initializeMetadata() {
    val primitiveTypeStringNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "String")
    val primitiveTypeIntNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "Int")
    val primitiveTypeListNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "List")

    var typeMetadata: StorageTypeMetadata

    typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "com.intellij.devkit.workspaceModel.jsonDump.TestSymbolicId",
                                                    properties = listOf(OwnPropertyMetadata(isComputable = false,
                                                                                            isKey = false,
                                                                                            isOpen = false,
                                                                                            name = "name",
                                                                                            valueType = primitiveTypeStringNotNullable,
                                                                                            withDefault = false),
                                                                        OwnPropertyMetadata(isComputable = false,
                                                                                            isKey = false,
                                                                                            isOpen = false,
                                                                                            name = "presentableName",
                                                                                            valueType = primitiveTypeStringNotNullable,
                                                                                            withDefault = false)),
                                                    supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId"))

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.devkit.workspaceModel.jsonDump.BaseTestEntity",
                                  entityDataFqName = "com.intellij.devkit.workspaceModel.jsonDump.impl.BaseTestEntityData",
                                  supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity",
                                                      "com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId"),
                                  properties = listOf(OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "entitySource",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.EntitySource")),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "name",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "children",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "com.intellij.devkit.workspaceModel.jsonDump.ChildEntity",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = false),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "singleChild",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                                                        entityFqName = "com.intellij.devkit.workspaceModel.jsonDump.SingleChild",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = true),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "listOfAbstract",
                                                                          valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                                            ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                    typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(
                                                                                                                      fqName = "com.intellij.devkit.workspaceModel.jsonDump.AbstractClass",
                                                                                                                      subclasses = listOf(
                                                                                                                        FinalClassMetadata.ClassMetadata(
                                                                                                                          fqName = "com.intellij.devkit.workspaceModel.jsonDump.ImplClass1",
                                                                                                                          properties = listOf(
                                                                                                                            OwnPropertyMetadata(
                                                                                                                              isComputable = false,
                                                                                                                              isKey = false,
                                                                                                                              isOpen = false,
                                                                                                                              name = "string",
                                                                                                                              valueType = primitiveTypeStringNotNullable,
                                                                                                                              withDefault = false),
                                                                                                                            OwnPropertyMetadata(
                                                                                                                              isComputable = false,
                                                                                                                              isKey = false,
                                                                                                                              isOpen = false,
                                                                                                                              name = "version",
                                                                                                                              valueType = primitiveTypeIntNotNullable,
                                                                                                                              withDefault = false)),
                                                                                                                          supertypes = listOf(
                                                                                                                            "com.intellij.devkit.workspaceModel.jsonDump.AbstractClass")),
                                                                                                                        FinalClassMetadata.ClassMetadata(
                                                                                                                          fqName = "com.intellij.devkit.workspaceModel.jsonDump.ImplClass2",
                                                                                                                          properties = listOf(
                                                                                                                            OwnPropertyMetadata(
                                                                                                                              isComputable = false,
                                                                                                                              isKey = false,
                                                                                                                              isOpen = false,
                                                                                                                              name = "name",
                                                                                                                              valueType = primitiveTypeStringNotNullable,
                                                                                                                              withDefault = false),
                                                                                                                            OwnPropertyMetadata(
                                                                                                                              isComputable = false,
                                                                                                                              isKey = false,
                                                                                                                              isOpen = false,
                                                                                                                              name = "string",
                                                                                                                              valueType = primitiveTypeStringNotNullable,
                                                                                                                              withDefault = false)),
                                                                                                                          supertypes = listOf(
                                                                                                                            "com.intellij.devkit.workspaceModel.jsonDump.AbstractClass"))),
                                                                                                                      supertypes = listOf()))),
                                                                                                                          primitive = primitiveTypeListNotNullable),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = true,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "symbolicId",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                                fqName = "com.intellij.devkit.workspaceModel.jsonDump.TestSymbolicId",
                                                                                                                                properties = listOf(
                                                                                                                                  OwnPropertyMetadata(
                                                                                                                                    isComputable = false,
                                                                                                                                    isKey = false,
                                                                                                                                    isOpen = false,
                                                                                                                                    name = "name",
                                                                                                                                    valueType = primitiveTypeStringNotNullable,
                                                                                                                                    withDefault = false),
                                                                                                                                  OwnPropertyMetadata(
                                                                                                                                    isComputable = false,
                                                                                                                                    isKey = false,
                                                                                                                                    isOpen = false,
                                                                                                                                    name = "presentableName",
                                                                                                                                    valueType = primitiveTypeStringNotNullable,
                                                                                                                                    withDefault = false)),
                                                                                                                                supertypes = listOf(
                                                                                                                                  "com.intellij.platform.workspace.storage.SymbolicEntityId"))),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.devkit.workspaceModel.jsonDump.ChildEntity",
                                  entityDataFqName = "com.intellij.devkit.workspaceModel.jsonDump.impl.ChildEntityData",
                                  supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity"),
                                  properties = listOf(OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "entitySource",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.EntitySource")),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "childName",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "parent",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "com.intellij.devkit.workspaceModel.jsonDump.BaseTestEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = false),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.devkit.workspaceModel.jsonDump.ExtensionChildEntity",
                                  entityDataFqName = "com.intellij.devkit.workspaceModel.jsonDump.impl.ExtensionChildEntityData",
                                  supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity"),
                                  properties = listOf(OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "entitySource",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.EntitySource")),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "extensionChildName",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "parent",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "com.intellij.devkit.workspaceModel.jsonDump.BaseTestEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = false),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "listOfUrls",
                                                                          valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                                            ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                    typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                      fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl"))),
                                                                                                                          primitive = primitiveTypeListNotNullable),
                                                                          withDefault = false)),
                                  extProperties = listOf(ExtPropertyMetadata(isComputable = false,
                                                                             isOpen = false,
                                                                             name = "extensionChildren",
                                                                             receiverFqn = "com.intellij.devkit.workspaceModel.jsonDump.BaseTestEntity",
                                                                             valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                           entityFqName = "com.intellij.devkit.workspaceModel.jsonDump.ExtensionChildEntity",
                                                                                                                           isChild = true,
                                                                                                                           isNullable = false),
                                                                             withDefault = false)),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.devkit.workspaceModel.jsonDump.SingleChild",
                                  entityDataFqName = "com.intellij.devkit.workspaceModel.jsonDump.impl.SingleChildData",
                                  supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity"),
                                  properties = listOf(OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "entitySource",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.EntitySource")),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "someData",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "parent",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                                                        entityFqName = "com.intellij.devkit.workspaceModel.jsonDump.BaseTestEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = false),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)
  }

  override fun initializeMetadataHash() {
    addMetadataHash(typeFqn = "com.intellij.devkit.workspaceModel.jsonDump.BaseTestEntity", metadataHash = 1978440464)
    addMetadataHash(typeFqn = "com.intellij.devkit.workspaceModel.jsonDump.ChildEntity", metadataHash = 1942527778)
    addMetadataHash(typeFqn = "com.intellij.devkit.workspaceModel.jsonDump.ExtensionChildEntity", metadataHash = -1045264058)
    addMetadataHash(typeFqn = "com.intellij.devkit.workspaceModel.jsonDump.SingleChild", metadataHash = 1237266012)
    addMetadataHash(typeFqn = "com.intellij.devkit.workspaceModel.jsonDump.AbstractClass", metadataHash = -2125859360)
    addMetadataHash(typeFqn = "com.intellij.devkit.workspaceModel.jsonDump.ImplClass1", metadataHash = -312361998)
    addMetadataHash(typeFqn = "com.intellij.devkit.workspaceModel.jsonDump.ImplClass2", metadataHash = -243329592)
    addMetadataHash(typeFqn = "com.intellij.devkit.workspaceModel.jsonDump.TestSymbolicId", metadataHash = -544617414)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.SymbolicEntityId", metadataHash = -1612564642)
  }
}
