package com.intellij.platform.workspace.jps.entities.impl

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
    val primitiveTypeIntNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "Int")
    val primitiveTypeStringNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "String")

    var typeMetadata: StorageTypeMetadata

    typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.jps.entities.LibraryId", properties = listOf(
      OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "codeCache", valueType = primitiveTypeIntNotNullable,
                          withDefault = false),
      OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "name", valueType = primitiveTypeStringNotNullable,
                          withDefault = false),
      OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "presentableName",
                          valueType = primitiveTypeStringNotNullable, withDefault = false),
      OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "tableId",
                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                              typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(
                                                                                fqName = "com.intellij.platform.workspace.jps.entities.LibraryTableId",
                                                                                subclasses = listOf(FinalClassMetadata.ObjectMetadata(
                                                                                  fqName = "com.intellij.platform.workspace.jps.entities.LibraryTableId\$ProjectLibraryTableId",
                                                                                  properties = listOf(
                                                                                    OwnPropertyMetadata(isComputable = false, isKey = false,
                                                                                                        isOpen = false, name = "level",
                                                                                                        valueType = primitiveTypeStringNotNullable,
                                                                                                        withDefault = false)),
                                                                                  supertypes = listOf(
                                                                                    "com.intellij.platform.workspace.jps.entities.LibraryTableId",
                                                                                    "java.io.Serializable")),
                                                                                                    FinalClassMetadata.ClassMetadata(
                                                                                                      fqName = "com.intellij.platform.workspace.jps.entities.LibraryTableId\$GlobalLibraryTableId",
                                                                                                      properties = listOf(
                                                                                                        OwnPropertyMetadata(
                                                                                                          isComputable = false,
                                                                                                          isKey = false, isOpen = false,
                                                                                                          name = "level",
                                                                                                          valueType = primitiveTypeStringNotNullable,
                                                                                                          withDefault = false)),
                                                                                                      supertypes = listOf(
                                                                                                        "com.intellij.platform.workspace.jps.entities.LibraryTableId",
                                                                                                        "java.io.Serializable")),
                                                                                                    FinalClassMetadata.ClassMetadata(
                                                                                                      fqName = "com.intellij.platform.workspace.jps.entities.LibraryTableId\$ModuleLibraryTableId",
                                                                                                      properties = listOf(
                                                                                                        OwnPropertyMetadata(
                                                                                                          isComputable = false,
                                                                                                          isKey = false, isOpen = false,
                                                                                                          name = "level",
                                                                                                          valueType = primitiveTypeStringNotNullable,
                                                                                                          withDefault = false),
                                                                                                        OwnPropertyMetadata(
                                                                                                          isComputable = false,
                                                                                                          isKey = false, isOpen = false,
                                                                                                          name = "moduleId",
                                                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                                            isNullable = false,
                                                                                                            typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                              fqName = "com.intellij.platform.workspace.jps.entities.ModuleId",
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
                                                                                                      supertypes = listOf(
                                                                                                        "com.intellij.platform.workspace.jps.entities.LibraryTableId",
                                                                                                        "java.io.Serializable"))),
                                                                                supertypes = listOf("java.io.Serializable"))),
                          withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId"))

    addMetadata(typeMetadata)

    typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.jps.entities.FacetId", properties = listOf(
      OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "name", valueType = primitiveTypeStringNotNullable,
                          withDefault = false),
      OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "parentId",
                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                              typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                fqName = "com.intellij.platform.workspace.jps.entities.ModuleId",
                                                                                properties = listOf(
                                                                                  OwnPropertyMetadata(isComputable = false, isKey = false,
                                                                                                      isOpen = false, name = "name",
                                                                                                      valueType = primitiveTypeStringNotNullable,
                                                                                                      withDefault = false),
                                                                                  OwnPropertyMetadata(isComputable = false, isKey = false,
                                                                                                      isOpen = false,
                                                                                                      name = "presentableName",
                                                                                                      valueType = primitiveTypeStringNotNullable,
                                                                                                      withDefault = false)),
                                                                                supertypes = listOf(
                                                                                  "com.intellij.platform.workspace.storage.SymbolicEntityId"))),
                          withDefault = false),
      OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "presentableName",
                          valueType = primitiveTypeStringNotNullable, withDefault = false),
      OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "type",
                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                              typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                fqName = "com.intellij.platform.workspace.jps.entities.FacetEntityTypeId",
                                                                                properties = listOf(
                                                                                  OwnPropertyMetadata(isComputable = false, isKey = false,
                                                                                                      isOpen = false, name = "name",
                                                                                                      valueType = primitiveTypeStringNotNullable,
                                                                                                      withDefault = false)),
                                                                                supertypes = listOf())), withDefault = false)),
                                                    supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId"))

    addMetadata(typeMetadata)

    typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.jps.entities.SdkId", properties = listOf(
      OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "name", valueType = primitiveTypeStringNotNullable,
                          withDefault = false),
      OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "presentableName",
                          valueType = primitiveTypeStringNotNullable, withDefault = false),
      OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "type", valueType = primitiveTypeStringNotNullable,
                          withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId"))

    addMetadata(typeMetadata)

    typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.jps.entities.ModuleId", properties = listOf(
      OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "name", valueType = primitiveTypeStringNotNullable,
                          withDefault = false),
      OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "presentableName",
                          valueType = primitiveTypeStringNotNullable, withDefault = false)),
                                                    supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId"))

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.jps.entities.SimpleEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.jps.entities.impl.SimpleEntityData",
                                  supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity"), properties = listOf(
        OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource",
                            valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                typeMetadata = FinalClassMetadata.KnownClass(
                                                                                  fqName = "com.intellij.platform.workspace.storage.EntitySource")),
                            withDefault = false),
        OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "version", valueType = primitiveTypeIntNotNullable,
                            withDefault = false),
        OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "name", valueType = primitiveTypeStringNotNullable,
                            withDefault = false)), extProperties = listOf(), isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.jps.entities.SimpleParentByExtension",
                                  entityDataFqName = "com.intellij.platform.workspace.jps.entities.impl.SimpleParentByExtensionData",
                                  supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity"), properties = listOf(
        OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource",
                            valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                typeMetadata = FinalClassMetadata.KnownClass(
                                                                                  fqName = "com.intellij.platform.workspace.storage.EntitySource")),
                            withDefault = false),
        OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "simpleName",
                            valueType = primitiveTypeStringNotNullable, withDefault = false),
        OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "simpleChild",
                            valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                          entityFqName = "com.intellij.platform.workspace.jps.entities.SimpleEntity",
                                                                          isChild = true, isNullable = true), withDefault = false)),
                                  extProperties = listOf(ExtPropertyMetadata(isComputable = false, isOpen = false, name = "simpleParent",
                                                                             receiverFqn = "com.intellij.platform.workspace.jps.entities.SimpleEntity",
                                                                             valueType = ValueTypeMetadata.EntityReference(
                                                                               connectionType = ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                               entityFqName = "com.intellij.platform.workspace.jps.entities.SimpleParentByExtension",
                                                                               isChild = false, isNullable = false), withDefault = false)),
                                  isAbstract = false)

    addMetadata(typeMetadata)
  }

  override fun initializeMetadataHash() {
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.entities.SimpleEntity", metadataHash = -410074104)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.entities.SimpleParentByExtension", metadataHash = -1690374807)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.SymbolicEntityId", metadataHash = -266259058)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.entities.FacetId", metadataHash = 2064524777)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.entities.ModuleId", metadataHash = -575206713)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.entities.FacetEntityTypeId", metadataHash = -963163377)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.entities.LibraryId", metadataHash = 1192700660)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.entities.LibraryTableId", metadataHash = -219154451)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.entities.LibraryTableId\$GlobalLibraryTableId",
                    metadataHash = 1911253865)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.entities.LibraryTableId\$ModuleLibraryTableId", metadataHash = 777479370)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.entities.LibraryTableId\$ProjectLibraryTableId",
                    metadataHash = -1574432194)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.entities.SdkId", metadataHash = 51502100)
  }

}
