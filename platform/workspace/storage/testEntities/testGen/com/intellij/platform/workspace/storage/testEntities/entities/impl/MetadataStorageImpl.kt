// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.impl

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
    val primitiveTypeListNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "List")
    val primitiveTypeBooleanNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "Boolean")
    val primitiveTypeIntNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "Int")
    val primitiveTypeSetNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "Set")
    val primitiveTypeStringNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = true, type = "String")
    val primitiveTypeIntNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = true, type = "Int")
    val primitiveTypeMapNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "Map")

    var typeMetadata: StorageTypeMetadata

    typeMetadata =
      FinalClassMetadata.ObjectMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.MyDummyParentSource",
                                        properties = listOf(OwnPropertyMetadata(isComputable = false,
                                                                                isKey = false,
                                                                                isOpen = false,
                                                                                name = "virtualFileUrl",
                                                                                valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                  isNullable = true,
                                                                                  typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                                                                                withDefault = false)),
                                        supertypes = listOf("com.intellij.platform.workspace.storage.DummyParentEntitySource",
                                                            "com.intellij.platform.workspace.storage.EntitySource"))

    addMetadata(typeMetadata)

    typeMetadata = FinalClassMetadata.ObjectMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.MySource",
                                                     properties = listOf(OwnPropertyMetadata(isComputable = false,
                                                                                             isKey = false,
                                                                                             isOpen = false,
                                                                                             name = "virtualFileUrl",
                                                                                             valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                               isNullable = true,
                                                                                               typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                 fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                                                                                             withDefault = false)),
                                                     supertypes = listOf("com.intellij.platform.workspace.storage.EntitySource"))

    addMetadata(typeMetadata)

    typeMetadata =
      FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.VFUEntitySource",
                                       properties = listOf(OwnPropertyMetadata(isComputable = false,
                                                                               isKey = false,
                                                                               isOpen = false,
                                                                               name = "vfu",
                                                                               valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                 isNullable = false,
                                                                                 typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                                                                               withDefault = false),
                                                           OwnPropertyMetadata(isComputable = false,
                                                                               isKey = false,
                                                                               isOpen = false,
                                                                               name = "virtualFileUrl",
                                                                               valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                 isNullable = false,
                                                                                 typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                                                                               withDefault = false)),
                                       supertypes = listOf("com.intellij.platform.workspace.storage.EntitySource"))

    addMetadata(typeMetadata)

    typeMetadata = FinalClassMetadata.ObjectMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.AnotherSource",
                                                     properties = listOf(OwnPropertyMetadata(isComputable = false,
                                                                                             isKey = false,
                                                                                             isOpen = false,
                                                                                             name = "virtualFileUrl",
                                                                                             valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                               isNullable = true,
                                                                                               typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                 fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                                                                                             withDefault = false)),
                                                     supertypes = listOf("com.intellij.platform.workspace.storage.EntitySource"))

    addMetadata(typeMetadata)

    typeMetadata =
      FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.SampleEntitySource",
                                       properties = listOf(OwnPropertyMetadata(isComputable = false,
                                                                               isKey = false,
                                                                               isOpen = false,
                                                                               name = "name",
                                                                               valueType = primitiveTypeStringNotNullable,
                                                                               withDefault = false),
                                                           OwnPropertyMetadata(isComputable = false,
                                                                               isKey = false,
                                                                               isOpen = false,
                                                                               name = "virtualFileUrl",
                                                                               valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                 isNullable = true,
                                                                                 typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                                                                               withDefault = false)),
                                       supertypes = listOf("com.intellij.platform.workspace.storage.EntitySource"))

    addMetadata(typeMetadata)

    typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.SecondPId",
                                                    properties = listOf(OwnPropertyMetadata(isComputable = false,
                                                                                            isKey = false,
                                                                                            isOpen = false,
                                                                                            name = "presentableName",
                                                                                            valueType = primitiveTypeStringNotNullable,
                                                                                            withDefault = false)),
                                                    supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId"))

    addMetadata(typeMetadata)

    typeMetadata =
      FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.HeadAbstractionSymbolicId",
                                       properties = listOf(OwnPropertyMetadata(isComputable = false,
                                                                               isKey = false,
                                                                               isOpen = false,
                                                                               name = "presentableName",
                                                                               valueType = primitiveTypeStringNotNullable,
                                                                               withDefault = false)),
                                       supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId"))

    addMetadata(typeMetadata)

    typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.OneSymbolicId",
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

    typeMetadata =
      FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.ParentWithId\$ParentId",
                                       properties = listOf(OwnPropertyMetadata(isComputable = false,
                                                                               isKey = false,
                                                                               isOpen = false,
                                                                               name = "id",
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

    typeMetadata =
      FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.ChildNameIdWithParentId",
                                       properties = listOf(OwnPropertyMetadata(isComputable = false,
                                                                               isKey = false,
                                                                               isOpen = false,
                                                                               name = "name",
                                                                               valueType = primitiveTypeStringNotNullable,
                                                                               withDefault = false),
                                                           OwnPropertyMetadata(isComputable = false,
                                                                               isKey = false,
                                                                               isOpen = false,
                                                                               name = "parentId",
                                                                               valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                 isNullable = false,
                                                                                 typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.ParentNameId",
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
                                                                               withDefault = false),
                                                           OwnPropertyMetadata(isComputable = false,
                                                                               isKey = false,
                                                                               isOpen = false,
                                                                               name = "presentableName",
                                                                               valueType = primitiveTypeStringNotNullable,
                                                                               withDefault = false)),
                                       supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId"))

    addMetadata(typeMetadata)

    typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.FirstPId",
                                                    properties = listOf(OwnPropertyMetadata(isComputable = false,
                                                                                            isKey = false,
                                                                                            isOpen = false,
                                                                                            name = "presentableName",
                                                                                            valueType = primitiveTypeStringNotNullable,
                                                                                            withDefault = false)),
                                                    supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId"))

    addMetadata(typeMetadata)

    typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.ComposedId",
                                                    properties = listOf(OwnPropertyMetadata(isComputable = false,
                                                                                            isKey = false,
                                                                                            isOpen = false,
                                                                                            name = "link",
                                                                                            valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                              isNullable = false,
                                                                                              typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                fqName = "com.intellij.platform.workspace.storage.testEntities.entities.NameId",
                                                                                                properties = listOf(OwnPropertyMetadata(
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
                                                                                                supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId"))),
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
                                                                                            name = "presentableName",
                                                                                            valueType = primitiveTypeStringNotNullable,
                                                                                            withDefault = false)),
                                                    supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId"))

    addMetadata(typeMetadata)

    typeMetadata =
      FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.LinkedListEntityId",
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

    typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.ParentNameId",
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

    typeMetadata =
      FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.TreeMultiparentSymbolicId",
                                       properties = listOf(OwnPropertyMetadata(isComputable = false,
                                                                               isKey = false,
                                                                               isOpen = false,
                                                                               name = "data",
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

    typeMetadata =
      FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.OoParentEntityId",
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

    typeMetadata =
      FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.ChildWithId\$ChildId",
                                       properties = listOf(OwnPropertyMetadata(isComputable = false,
                                                                               isKey = false,
                                                                               isOpen = false,
                                                                               name = "id",
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

    typeMetadata =
      FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.GrandParentWithId\$GrandParentId",
                                       properties = listOf(OwnPropertyMetadata(isComputable = false,
                                                                               isKey = false,
                                                                               isOpen = false,
                                                                               name = "id",
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

    typeMetadata =
      FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.OoChildEntityId",
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

    typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.AnotherNameId",
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

    typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.NameId",
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

    typeMetadata =
      FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.ModuleTestEntitySymbolicId",
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

    typeMetadata =
      FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.SampleSymbolicId",
                                       properties = listOf(OwnPropertyMetadata(isComputable = false,
                                                                               isKey = false,
                                                                               isOpen = false,
                                                                               name = "presentableName",
                                                                               valueType = primitiveTypeStringNotNullable,
                                                                               withDefault = false),
                                                           OwnPropertyMetadata(isComputable = false,
                                                                               isKey = false,
                                                                               isOpen = false,
                                                                               name = "stringProperty",
                                                                               valueType = primitiveTypeStringNotNullable,
                                                                               withDefault = false)),
                                       supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId"))

    addMetadata(typeMetadata)

    typeMetadata =
      FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.FacetTestEntitySymbolicId",
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

    typeMetadata =
      FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ChangedComputablePropEntityId",
                                       properties = listOf(OwnPropertyMetadata(isComputable = false,
                                                                               isKey = false,
                                                                               isOpen = false,
                                                                               name = "presentableName",
                                                                               valueType = primitiveTypeStringNotNullable,
                                                                               withDefault = false),
                                                           OwnPropertyMetadata(isComputable = false,
                                                                               isKey = false,
                                                                               isOpen = false,
                                                                               name = "text",
                                                                               valueType = primitiveTypeStringNotNullable,
                                                                               withDefault = false)),
                                       supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId"))

    addMetadata(typeMetadata)

    typeMetadata =
      FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ChangedComputablePropsOrderEntityId",
                                       properties = listOf(OwnPropertyMetadata(isComputable = false,
                                                                               isKey = false,
                                                                               isOpen = false,
                                                                               name = "names",
                                                                               valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                                                 primitiveTypeStringNotNullable),
                                                                                                                               primitive = primitiveTypeListNotNullable),
                                                                               withDefault = false),
                                                           OwnPropertyMetadata(isComputable = false,
                                                                               isKey = false,
                                                                               isOpen = false,
                                                                               name = "presentableName",
                                                                               valueType = primitiveTypeStringNotNullable,
                                                                               withDefault = false)),
                                       supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId"))

    addMetadata(typeMetadata)

    typeMetadata =
      FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ChangedComputablePropEntityId",
                                       properties = listOf(OwnPropertyMetadata(isComputable = false,
                                                                               isKey = false,
                                                                               isOpen = false,
                                                                               name = "presentableName",
                                                                               valueType = primitiveTypeStringNotNullable,
                                                                               withDefault = false),
                                                           OwnPropertyMetadata(isComputable = false,
                                                                               isKey = false,
                                                                               isOpen = false,
                                                                               name = "texts",
                                                                               valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                                                 primitiveTypeStringNotNullable),
                                                                                                                               primitive = primitiveTypeListNotNullable),
                                                                               withDefault = false)),
                                       supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId"))

    addMetadata(typeMetadata)

    typeMetadata =
      FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ChangedComputablePropsOrderEntityId",
                                       properties = listOf(OwnPropertyMetadata(isComputable = false,
                                                                               isKey = false,
                                                                               isOpen = false,
                                                                               name = "names",
                                                                               valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                                                 primitiveTypeStringNotNullable),
                                                                                                                               primitive = primitiveTypeListNotNullable),
                                                                               withDefault = false),
                                                           OwnPropertyMetadata(isComputable = false,
                                                                               isKey = false,
                                                                               isOpen = false,
                                                                               name = "presentableName",
                                                                               valueType = primitiveTypeStringNotNullable,
                                                                               withDefault = false)),
                                       supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId"))

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.AbstractChildEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.AbstractChildEntityData",
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
                                                                          name = "data",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "parent",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.ParentWithExtensionEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = false),
                                                                          withDefault = false)),
                                  extProperties = listOf(ExtPropertyMetadata(isComputable = false,
                                                                             isOpen = false,
                                                                             name = "child",
                                                                             receiverFqn = "com.intellij.platform.workspace.storage.testEntities.entities.ParentWithExtensionEntity",
                                                                             valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE,
                                                                                                                           entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.AbstractChildEntity",
                                                                                                                           isChild = true,
                                                                                                                           isNullable = true),
                                                                             withDefault = false)),
                                  isAbstract = true)

    addMetadata(typeMetadata)

    typeMetadata =
      EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.AbstractChildWithLinkToParentEntity",
                     entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.AbstractChildWithLinkToParentEntityData",
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
                                                             name = "data",
                                                             valueType = primitiveTypeStringNotNullable,
                                                             withDefault = false)),
                     extProperties = listOf(),
                     isAbstract = true)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.AbstractParentEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.AbstractParentEntityData",
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
                                                                          name = "data",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "child",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.ChildWithExtensionParent",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = true),
                                                                          withDefault = false)),
                                  extProperties = listOf(ExtPropertyMetadata(isComputable = false,
                                                                             isOpen = false,
                                                                             name = "parent",
                                                                             receiverFqn = "com.intellij.platform.workspace.storage.testEntities.entities.ChildWithExtensionParent",
                                                                             valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                                                           entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.AbstractParentEntity",
                                                                                                                           isChild = false,
                                                                                                                           isNullable = true),
                                                                             withDefault = false)),
                                  isAbstract = true)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.AssertConsistencyEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.AssertConsistencyEntityData",
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
                                                                          name = "passCheck",
                                                                          valueType = primitiveTypeBooleanNotNullable,
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.AttachedEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.AttachedEntityData",
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
                                                                          name = "ref",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.MainEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = false),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "data",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false)),
                                  extProperties = listOf(ExtPropertyMetadata(isComputable = false,
                                                                             isOpen = false,
                                                                             name = "child",
                                                                             receiverFqn = "com.intellij.platform.workspace.storage.testEntities.entities.MainEntity",
                                                                             valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                                                           entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.AttachedEntity",
                                                                                                                           isChild = true,
                                                                                                                           isNullable = true),
                                                                             withDefault = false)),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.AttachedEntityList",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.AttachedEntityListData",
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
                                                                          name = "ref",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.MainEntityList",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = true),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "data",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false)),
                                  extProperties = listOf(ExtPropertyMetadata(isComputable = false,
                                                                             isOpen = false,
                                                                             name = "child",
                                                                             receiverFqn = "com.intellij.platform.workspace.storage.testEntities.entities.MainEntityList",
                                                                             valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                           entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.AttachedEntityList",
                                                                                                                           isChild = true,
                                                                                                                           isNullable = false),
                                                                             withDefault = false)),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.AttachedEntityParentList",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.AttachedEntityParentListData",
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
                                                                          name = "data",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.AttachedEntityToNullableParent",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.AttachedEntityToNullableParentData",
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
                                                                          name = "data",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.AttachedEntityToParent",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.AttachedEntityToParentData",
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
                                                                          name = "data",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.BaseEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.BaseEntityData",
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
                                                                          name = "parentEntity",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.CompositeBaseEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = true),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = true)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.BooleanEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.BooleanEntityData",
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
                                                                          name = "data",
                                                                          valueType = primitiveTypeBooleanNotNullable,
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.ChainedEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.ChainedEntityData",
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
                                                                          name = "data",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "parent",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.ChainedEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = true),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "child",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.ChainedEntity",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = true),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "generalParent",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.ChainedParentEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = true),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.ChainedParentEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.ChainedParentEntityData",
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
                                                                          name = "child",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.ChainedEntity",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = false),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.ChildAbstractBaseEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.ChildAbstractBaseEntityData",
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
                                                                          name = "commonData",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "parentEntity",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.ParentAbEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = false),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = true)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.ChildEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.ChildEntityData",
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
                                                                          name = "childData",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "parentEntity",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.ParentEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = false),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.ChildEntityWithSymbolicId",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.ChildEntityWithSymbolicIdData",
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
                                                                          name = "myName",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "parent",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.ParentEntityWithSymbolicId",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = false),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = true,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "symbolicId",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.testEntities.entities.ChildNameIdWithParentId",
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
                                                                                                                                    name = "parentId",
                                                                                                                                    valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                                                                      isNullable = false,
                                                                                                                                      typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                                        fqName = "com.intellij.platform.workspace.storage.testEntities.entities.ParentNameId",
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

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.ChildFirstEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.ChildFirstEntityData",
                                  supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity",
                                                      "com.intellij.platform.workspace.storage.testEntities.entities.ChildAbstractBaseEntity"),
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
                                                                          name = "commonData",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "parentEntity",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.ParentAbEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = false),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "firstData",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.ChildMultipleEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.ChildMultipleEntityData",
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
                                                                          name = "childData",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "parentEntity",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.ParentMultipleEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = false),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.ChildSampleEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.ChildSampleEntityData",
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
                                                                          name = "data",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "parentEntity",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.SampleEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = true),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.ChildSecondEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.ChildSecondEntityData",
                                  supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity",
                                                      "com.intellij.platform.workspace.storage.testEntities.entities.ChildAbstractBaseEntity"),
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
                                                                          name = "commonData",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "parentEntity",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.ParentAbEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = false),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "secondData",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.ChildSingleAbstractBaseEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.ChildSingleAbstractBaseEntityData",
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
                                                                          name = "commonData",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "parentEntity",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.ParentSingleAbEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = false),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = true)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.ChildSingleFirstEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.ChildSingleFirstEntityData",
                                  supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity",
                                                      "com.intellij.platform.workspace.storage.testEntities.entities.ChildSingleAbstractBaseEntity"),
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
                                                                          name = "commonData",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "parentEntity",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.ParentSingleAbEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = false),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "firstData",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.ChildSingleSecondEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.ChildSingleSecondEntityData",
                                  supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity",
                                                      "com.intellij.platform.workspace.storage.testEntities.entities.ChildSingleAbstractBaseEntity"),
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
                                                                          name = "commonData",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "parentEntity",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.ParentSingleAbEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = false),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "secondData",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.ChildSourceEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.ChildSourceEntityData",
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
                                                                          name = "data",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "parentEntity",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.SourceEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = false),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.ChildSubEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.ChildSubEntityData",
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
                                                                          name = "parentEntity",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.ParentSubEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = false),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "child",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.ChildSubSubEntity",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = true),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.ChildSubSubEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.ChildSubSubEntityData",
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
                                                                          name = "parentEntity",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.ChildSubEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = false),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "childData",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.ChildWithExtensionParent",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.ChildWithExtensionParentData",
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
                                                                          name = "data",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.ChildWithId",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.ChildWithIdData",
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
                                                                          name = "myId",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "parent",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.ParentWithId",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = false),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = true,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "symbolicId",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.testEntities.entities.ChildWithId\$ChildId",
                                                                                                                                properties = listOf(
                                                                                                                                  OwnPropertyMetadata(
                                                                                                                                    isComputable = false,
                                                                                                                                    isKey = false,
                                                                                                                                    isOpen = false,
                                                                                                                                    name = "id",
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

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.ChildWithNulls",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.ChildWithNullsData",
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
                                                                          name = "childData",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.ChildWithNullsMultiple",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.ChildWithNullsMultipleData",
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
                                                                          name = "childData",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.ChildWithNullsOppositeMultiple",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.ChildWithNullsOppositeMultipleData",
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
                                                                          name = "childData",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "parentEntity",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.ParentWithNullsOppositeMultiple",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = true),
                                                                          withDefault = false)),
                                  extProperties = listOf(ExtPropertyMetadata(isComputable = false,
                                                                             isOpen = false,
                                                                             name = "children",
                                                                             receiverFqn = "com.intellij.platform.workspace.storage.testEntities.entities.ParentWithNullsOppositeMultiple",
                                                                             valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                           entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.ChildWithNullsOppositeMultiple",
                                                                                                                           isChild = true,
                                                                                                                           isNullable = false),
                                                                             withDefault = false)),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.ChildWpidSampleEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.ChildWpidSampleEntityData",
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
                                                                          name = "data",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "parentEntity",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.SampleWithSymbolicIdEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = true),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.CollectionFieldEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.CollectionFieldEntityData",
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
                                                                          name = "versions",
                                                                          valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                                            primitiveTypeIntNotNullable),
                                                                                                                          primitive = primitiveTypeSetNotNullable),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "names",
                                                                          valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                                            primitiveTypeStringNotNullable),
                                                                                                                          primitive = primitiveTypeListNotNullable),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.ComposedIdSoftRefEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.ComposedIdSoftRefEntityData",
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
                                                                          name = "myName",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "link",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.testEntities.entities.NameId",
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
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = true,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "symbolicId",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.testEntities.entities.ComposedId",
                                                                                                                                properties = listOf(
                                                                                                                                  OwnPropertyMetadata(
                                                                                                                                    isComputable = false,
                                                                                                                                    isKey = false,
                                                                                                                                    isOpen = false,
                                                                                                                                    name = "link",
                                                                                                                                    valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                                                                      isNullable = false,
                                                                                                                                      typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                                        fqName = "com.intellij.platform.workspace.storage.testEntities.entities.NameId",
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
                                                                                                                                    withDefault = false),
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

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.ComposedLinkEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.ComposedLinkEntityData",
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
                                                                          name = "link",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.testEntities.entities.ComposedId",
                                                                                                                                properties = listOf(
                                                                                                                                  OwnPropertyMetadata(
                                                                                                                                    isComputable = false,
                                                                                                                                    isKey = false,
                                                                                                                                    isOpen = false,
                                                                                                                                    name = "link",
                                                                                                                                    valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                                                                      isNullable = false,
                                                                                                                                      typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                                        fqName = "com.intellij.platform.workspace.storage.testEntities.entities.NameId",
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
                                                                                                                                    withDefault = false),
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

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.CompositeAbstractEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.CompositeAbstractEntityData",
                                  supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity",
                                                      "com.intellij.platform.workspace.storage.testEntities.entities.SimpleAbstractEntity"),
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
                                                                          name = "parentInList",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.CompositeAbstractEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = true),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "children",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.SimpleAbstractEntity",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = false),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "parentEntity",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.ParentChainEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = true),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = true)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.CompositeBaseEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.CompositeBaseEntityData",
                                  supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity",
                                                      "com.intellij.platform.workspace.storage.testEntities.entities.BaseEntity"),
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
                                                                          name = "parentEntity",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.CompositeBaseEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = true),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "children",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.BaseEntity",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = false),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "parent",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.HeadAbstractionEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = true),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = true)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.CompositeChildAbstractEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.CompositeChildAbstractEntityData",
                                  supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity",
                                                      "com.intellij.platform.workspace.storage.testEntities.entities.SimpleAbstractEntity",
                                                      "com.intellij.platform.workspace.storage.testEntities.entities.CompositeAbstractEntity"),
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
                                                                          name = "parentInList",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.CompositeAbstractEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = true),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "children",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.SimpleAbstractEntity",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = false),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "parentEntity",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.ParentChainEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = true),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.ContentRootTestEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.ContentRootTestEntityData",
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
                                                                          name = "module",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.ModuleTestEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = false),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "sourceRootOrder",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.SourceRootTestOrderEntity",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = true),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "sourceRoots",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.SourceRootTestEntity",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = false),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.DefaultValueEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.DefaultValueEntityData",
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
                                                                          name = "name",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "isGenerated",
                                                                          valueType = primitiveTypeBooleanNotNullable,
                                                                          withDefault = true),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "anotherName",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = true)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.EntityWithSoftLinks",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.EntityWithSoftLinksData",
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
                                                                          name = "link",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.testEntities.entities.OneSymbolicId",
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
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "manyLinks",
                                                                          valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                                            ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                    typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                      fqName = "com.intellij.platform.workspace.storage.testEntities.entities.OneSymbolicId",
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
                                                                                                                      supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId")))),
                                                                                                                          primitive = primitiveTypeListNotNullable),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "optionalLink",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = true,
                                                                                                                              typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.testEntities.entities.OneSymbolicId",
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
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "inContainer",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.testEntities.entities.Container",
                                                                                                                                properties = listOf(
                                                                                                                                  OwnPropertyMetadata(
                                                                                                                                    isComputable = false,
                                                                                                                                    isKey = false,
                                                                                                                                    isOpen = false,
                                                                                                                                    name = "id",
                                                                                                                                    valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                                                                      isNullable = false,
                                                                                                                                      typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                                        fqName = "com.intellij.platform.workspace.storage.testEntities.entities.OneSymbolicId",
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
                                                                                                                                    withDefault = false),
                                                                                                                                  OwnPropertyMetadata(
                                                                                                                                    isComputable = false,
                                                                                                                                    isKey = false,
                                                                                                                                    isOpen = false,
                                                                                                                                    name = "justData",
                                                                                                                                    valueType = primitiveTypeStringNotNullable,
                                                                                                                                    withDefault = false)),
                                                                                                                                supertypes = listOf())),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "inOptionalContainer",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = true,
                                                                                                                              typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.testEntities.entities.Container",
                                                                                                                                properties = listOf(
                                                                                                                                  OwnPropertyMetadata(
                                                                                                                                    isComputable = false,
                                                                                                                                    isKey = false,
                                                                                                                                    isOpen = false,
                                                                                                                                    name = "id",
                                                                                                                                    valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                                                                      isNullable = false,
                                                                                                                                      typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                                        fqName = "com.intellij.platform.workspace.storage.testEntities.entities.OneSymbolicId",
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
                                                                                                                                    withDefault = false),
                                                                                                                                  OwnPropertyMetadata(
                                                                                                                                    isComputable = false,
                                                                                                                                    isKey = false,
                                                                                                                                    isOpen = false,
                                                                                                                                    name = "justData",
                                                                                                                                    valueType = primitiveTypeStringNotNullable,
                                                                                                                                    withDefault = false)),
                                                                                                                                supertypes = listOf())),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "inContainerList",
                                                                          valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                                            ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                    typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                      fqName = "com.intellij.platform.workspace.storage.testEntities.entities.Container",
                                                                                                                      properties = listOf(
                                                                                                                        OwnPropertyMetadata(
                                                                                                                          isComputable = false,
                                                                                                                          isKey = false,
                                                                                                                          isOpen = false,
                                                                                                                          name = "id",
                                                                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                                                            isNullable = false,
                                                                                                                            typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                              fqName = "com.intellij.platform.workspace.storage.testEntities.entities.OneSymbolicId",
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
                                                                                                                          withDefault = false),
                                                                                                                        OwnPropertyMetadata(
                                                                                                                          isComputable = false,
                                                                                                                          isKey = false,
                                                                                                                          isOpen = false,
                                                                                                                          name = "justData",
                                                                                                                          valueType = primitiveTypeStringNotNullable,
                                                                                                                          withDefault = false)),
                                                                                                                      supertypes = listOf()))),
                                                                                                                          primitive = primitiveTypeListNotNullable),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "deepContainer",
                                                                          valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                                            ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                    typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                      fqName = "com.intellij.platform.workspace.storage.testEntities.entities.TooDeepContainer",
                                                                                                                      properties = listOf(
                                                                                                                        OwnPropertyMetadata(
                                                                                                                          isComputable = false,
                                                                                                                          isKey = false,
                                                                                                                          isOpen = false,
                                                                                                                          name = "goDeeper",
                                                                                                                          valueType = ValueTypeMetadata.ParameterizedType(
                                                                                                                            generics = listOf(
                                                                                                                              ValueTypeMetadata.SimpleType.CustomType(
                                                                                                                                isNullable = false,
                                                                                                                                typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                                  fqName = "com.intellij.platform.workspace.storage.testEntities.entities.DeepContainer",
                                                                                                                                  properties = listOf(
                                                                                                                                    OwnPropertyMetadata(
                                                                                                                                      isComputable = false,
                                                                                                                                      isKey = false,
                                                                                                                                      isOpen = false,
                                                                                                                                      name = "goDeep",
                                                                                                                                      valueType = ValueTypeMetadata.ParameterizedType(
                                                                                                                                        generics = listOf(
                                                                                                                                          ValueTypeMetadata.SimpleType.CustomType(
                                                                                                                                            isNullable = false,
                                                                                                                                            typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                                              fqName = "com.intellij.platform.workspace.storage.testEntities.entities.Container",
                                                                                                                                              properties = listOf(
                                                                                                                                                OwnPropertyMetadata(
                                                                                                                                                  isComputable = false,
                                                                                                                                                  isKey = false,
                                                                                                                                                  isOpen = false,
                                                                                                                                                  name = "id",
                                                                                                                                                  valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                                                                                    isNullable = false,
                                                                                                                                                    typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                                                      fqName = "com.intellij.platform.workspace.storage.testEntities.entities.OneSymbolicId",
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
                                                                                                                                                  withDefault = false),
                                                                                                                                                OwnPropertyMetadata(
                                                                                                                                                  isComputable = false,
                                                                                                                                                  isKey = false,
                                                                                                                                                  isOpen = false,
                                                                                                                                                  name = "justData",
                                                                                                                                                  valueType = primitiveTypeStringNotNullable,
                                                                                                                                                  withDefault = false)),
                                                                                                                                              supertypes = listOf()))),
                                                                                                                                        primitive = primitiveTypeListNotNullable),
                                                                                                                                      withDefault = false),
                                                                                                                                    OwnPropertyMetadata(
                                                                                                                                      isComputable = false,
                                                                                                                                      isKey = false,
                                                                                                                                      isOpen = false,
                                                                                                                                      name = "optionalId",
                                                                                                                                      valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                                                                        isNullable = true,
                                                                                                                                        typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                                          fqName = "com.intellij.platform.workspace.storage.testEntities.entities.OneSymbolicId")),
                                                                                                                                      withDefault = false)),
                                                                                                                                  supertypes = listOf()))),
                                                                                                                            primitive = primitiveTypeListNotNullable),
                                                                                                                          withDefault = false)),
                                                                                                                      supertypes = listOf()))),
                                                                                                                          primitive = primitiveTypeListNotNullable),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "sealedContainer",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.testEntities.entities.SealedContainer",
                                                                                                                                subclasses = listOf(
                                                                                                                                  FinalClassMetadata.ClassMetadata(
                                                                                                                                    fqName = "com.intellij.platform.workspace.storage.testEntities.entities.SealedContainer\$ContainerContainer",
                                                                                                                                    properties = listOf(
                                                                                                                                      OwnPropertyMetadata(
                                                                                                                                        isComputable = false,
                                                                                                                                        isKey = false,
                                                                                                                                        isOpen = false,
                                                                                                                                        name = "container",
                                                                                                                                        valueType = ValueTypeMetadata.ParameterizedType(
                                                                                                                                          generics = listOf(
                                                                                                                                            ValueTypeMetadata.SimpleType.CustomType(
                                                                                                                                              isNullable = false,
                                                                                                                                              typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                                                fqName = "com.intellij.platform.workspace.storage.testEntities.entities.Container",
                                                                                                                                                properties = listOf(
                                                                                                                                                  OwnPropertyMetadata(
                                                                                                                                                    isComputable = false,
                                                                                                                                                    isKey = false,
                                                                                                                                                    isOpen = false,
                                                                                                                                                    name = "id",
                                                                                                                                                    valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                                                                                      isNullable = false,
                                                                                                                                                      typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                                                        fqName = "com.intellij.platform.workspace.storage.testEntities.entities.OneSymbolicId")),
                                                                                                                                                    withDefault = false),
                                                                                                                                                  OwnPropertyMetadata(
                                                                                                                                                    isComputable = false,
                                                                                                                                                    isKey = false,
                                                                                                                                                    isOpen = false,
                                                                                                                                                    name = "justData",
                                                                                                                                                    valueType = primitiveTypeStringNotNullable,
                                                                                                                                                    withDefault = false)),
                                                                                                                                                supertypes = listOf()))),
                                                                                                                                          primitive = primitiveTypeListNotNullable),
                                                                                                                                        withDefault = false)),
                                                                                                                                    supertypes = listOf(
                                                                                                                                      "com.intellij.platform.workspace.storage.testEntities.entities.SealedContainer")),
                                                                                                                                  FinalClassMetadata.ClassMetadata(
                                                                                                                                    fqName = "com.intellij.platform.workspace.storage.testEntities.entities.SealedContainer\$EmptyContainer",
                                                                                                                                    properties = listOf(
                                                                                                                                      OwnPropertyMetadata(
                                                                                                                                        isComputable = false,
                                                                                                                                        isKey = false,
                                                                                                                                        isOpen = false,
                                                                                                                                        name = "data",
                                                                                                                                        valueType = primitiveTypeStringNotNullable,
                                                                                                                                        withDefault = false)),
                                                                                                                                    supertypes = listOf(
                                                                                                                                      "com.intellij.platform.workspace.storage.testEntities.entities.SealedContainer")),
                                                                                                                                  FinalClassMetadata.ClassMetadata(
                                                                                                                                    fqName = "com.intellij.platform.workspace.storage.testEntities.entities.SealedContainer\$SmallContainer",
                                                                                                                                    properties = listOf(
                                                                                                                                      OwnPropertyMetadata(
                                                                                                                                        isComputable = false,
                                                                                                                                        isKey = false,
                                                                                                                                        isOpen = false,
                                                                                                                                        name = "notId",
                                                                                                                                        valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                                                                          isNullable = false,
                                                                                                                                          typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                                            fqName = "com.intellij.platform.workspace.storage.testEntities.entities.OneSymbolicId")),
                                                                                                                                        withDefault = false)),
                                                                                                                                    supertypes = listOf(
                                                                                                                                      "com.intellij.platform.workspace.storage.testEntities.entities.SealedContainer")),
                                                                                                                                  FinalClassMetadata.ClassMetadata(
                                                                                                                                    fqName = "com.intellij.platform.workspace.storage.testEntities.entities.SealedContainer\$BigContainer",
                                                                                                                                    properties = listOf(
                                                                                                                                      OwnPropertyMetadata(
                                                                                                                                        isComputable = false,
                                                                                                                                        isKey = false,
                                                                                                                                        isOpen = false,
                                                                                                                                        name = "id",
                                                                                                                                        valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                                                                          isNullable = false,
                                                                                                                                          typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                                            fqName = "com.intellij.platform.workspace.storage.testEntities.entities.OneSymbolicId",
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
                                                                                                                                      "com.intellij.platform.workspace.storage.testEntities.entities.SealedContainer"))),
                                                                                                                                supertypes = listOf())),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "listSealedContainer",
                                                                          valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                                            ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                    typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(
                                                                                                                      fqName = "com.intellij.platform.workspace.storage.testEntities.entities.SealedContainer",
                                                                                                                      subclasses = listOf(
                                                                                                                        FinalClassMetadata.ClassMetadata(
                                                                                                                          fqName = "com.intellij.platform.workspace.storage.testEntities.entities.SealedContainer\$ContainerContainer",
                                                                                                                          properties = listOf(
                                                                                                                            OwnPropertyMetadata(
                                                                                                                              isComputable = false,
                                                                                                                              isKey = false,
                                                                                                                              isOpen = false,
                                                                                                                              name = "container",
                                                                                                                              valueType = ValueTypeMetadata.ParameterizedType(
                                                                                                                                generics = listOf(
                                                                                                                                  ValueTypeMetadata.SimpleType.CustomType(
                                                                                                                                    isNullable = false,
                                                                                                                                    typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                                      fqName = "com.intellij.platform.workspace.storage.testEntities.entities.Container",
                                                                                                                                      properties = listOf(
                                                                                                                                        OwnPropertyMetadata(
                                                                                                                                          isComputable = false,
                                                                                                                                          isKey = false,
                                                                                                                                          isOpen = false,
                                                                                                                                          name = "id",
                                                                                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                                                                            isNullable = false,
                                                                                                                                            typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                                              fqName = "com.intellij.platform.workspace.storage.testEntities.entities.OneSymbolicId")),
                                                                                                                                          withDefault = false),
                                                                                                                                        OwnPropertyMetadata(
                                                                                                                                          isComputable = false,
                                                                                                                                          isKey = false,
                                                                                                                                          isOpen = false,
                                                                                                                                          name = "justData",
                                                                                                                                          valueType = primitiveTypeStringNotNullable,
                                                                                                                                          withDefault = false)),
                                                                                                                                      supertypes = listOf()))),
                                                                                                                                primitive = primitiveTypeListNotNullable),
                                                                                                                              withDefault = false)),
                                                                                                                          supertypes = listOf(
                                                                                                                            "com.intellij.platform.workspace.storage.testEntities.entities.SealedContainer")),
                                                                                                                        FinalClassMetadata.ClassMetadata(
                                                                                                                          fqName = "com.intellij.platform.workspace.storage.testEntities.entities.SealedContainer\$EmptyContainer",
                                                                                                                          properties = listOf(
                                                                                                                            OwnPropertyMetadata(
                                                                                                                              isComputable = false,
                                                                                                                              isKey = false,
                                                                                                                              isOpen = false,
                                                                                                                              name = "data",
                                                                                                                              valueType = primitiveTypeStringNotNullable,
                                                                                                                              withDefault = false)),
                                                                                                                          supertypes = listOf(
                                                                                                                            "com.intellij.platform.workspace.storage.testEntities.entities.SealedContainer")),
                                                                                                                        FinalClassMetadata.ClassMetadata(
                                                                                                                          fqName = "com.intellij.platform.workspace.storage.testEntities.entities.SealedContainer\$SmallContainer",
                                                                                                                          properties = listOf(
                                                                                                                            OwnPropertyMetadata(
                                                                                                                              isComputable = false,
                                                                                                                              isKey = false,
                                                                                                                              isOpen = false,
                                                                                                                              name = "notId",
                                                                                                                              valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                                                                isNullable = false,
                                                                                                                                typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                                  fqName = "com.intellij.platform.workspace.storage.testEntities.entities.OneSymbolicId")),
                                                                                                                              withDefault = false)),
                                                                                                                          supertypes = listOf(
                                                                                                                            "com.intellij.platform.workspace.storage.testEntities.entities.SealedContainer")),
                                                                                                                        FinalClassMetadata.ClassMetadata(
                                                                                                                          fqName = "com.intellij.platform.workspace.storage.testEntities.entities.SealedContainer\$BigContainer",
                                                                                                                          properties = listOf(
                                                                                                                            OwnPropertyMetadata(
                                                                                                                              isComputable = false,
                                                                                                                              isKey = false,
                                                                                                                              isOpen = false,
                                                                                                                              name = "id",
                                                                                                                              valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                                                                isNullable = false,
                                                                                                                                typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                                  fqName = "com.intellij.platform.workspace.storage.testEntities.entities.OneSymbolicId",
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
                                                                                                                            "com.intellij.platform.workspace.storage.testEntities.entities.SealedContainer"))),
                                                                                                                      supertypes = listOf()))),
                                                                                                                          primitive = primitiveTypeListNotNullable),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "justProperty",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "justNullableProperty",
                                                                          valueType = primitiveTypeStringNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "justListProperty",
                                                                          valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                                            primitiveTypeStringNotNullable),
                                                                                                                          primitive = primitiveTypeListNotNullable),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "deepSealedClass",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.testEntities.entities.DeepSealedOne",
                                                                                                                                subclasses = listOf(
                                                                                                                                  FinalClassMetadata.ClassMetadata(
                                                                                                                                    fqName = "com.intellij.platform.workspace.storage.testEntities.entities.DeepSealedOne\$DeepSealedTwo\$DeepSealedThree\$DeepSealedFour",
                                                                                                                                    properties = listOf(
                                                                                                                                      OwnPropertyMetadata(
                                                                                                                                        isComputable = false,
                                                                                                                                        isKey = false,
                                                                                                                                        isOpen = false,
                                                                                                                                        name = "id",
                                                                                                                                        valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                                                                          isNullable = false,
                                                                                                                                          typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                                            fqName = "com.intellij.platform.workspace.storage.testEntities.entities.OneSymbolicId",
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
                                                                                                                                      "com.intellij.platform.workspace.storage.testEntities.entities.DeepSealedOne",
                                                                                                                                      "com.intellij.platform.workspace.storage.testEntities.entities.DeepSealedOne\$DeepSealedTwo",
                                                                                                                                      "com.intellij.platform.workspace.storage.testEntities.entities.DeepSealedOne\$DeepSealedTwo\$DeepSealedThree"))),
                                                                                                                                supertypes = listOf())),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "children",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.SoftLinkReferencedChild",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = false),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.FacetTestEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.FacetTestEntityData",
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
                                                                          name = "data",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "moreData",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "module",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.ModuleTestEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = false),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = true,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "symbolicId",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.testEntities.entities.FacetTestEntitySymbolicId",
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

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.FinalFieldsEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.FinalFieldsEntityData",
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
                                                                          name = "descriptor",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.testEntities.entities.AnotherDataClass",
                                                                                                                                properties = listOf(
                                                                                                                                  OwnPropertyMetadata(
                                                                                                                                    isComputable = false,
                                                                                                                                    isKey = false,
                                                                                                                                    isOpen = false,
                                                                                                                                    name = "displayName",
                                                                                                                                    valueType = primitiveTypeStringNullable,
                                                                                                                                    withDefault = false),
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
                                                                                                                                    name = "revision",
                                                                                                                                    valueType = primitiveTypeStringNullable,
                                                                                                                                    withDefault = false),
                                                                                                                                  OwnPropertyMetadata(
                                                                                                                                    isComputable = false,
                                                                                                                                    isKey = false,
                                                                                                                                    isOpen = false,
                                                                                                                                    name = "source",
                                                                                                                                    valueType = primitiveTypeBooleanNotNullable,
                                                                                                                                    withDefault = false),
                                                                                                                                  OwnPropertyMetadata(
                                                                                                                                    isComputable = false,
                                                                                                                                    isKey = false,
                                                                                                                                    isOpen = false,
                                                                                                                                    name = "url",
                                                                                                                                    valueType = primitiveTypeStringNullable,
                                                                                                                                    withDefault = false),
                                                                                                                                  OwnPropertyMetadata(
                                                                                                                                    isComputable = false,
                                                                                                                                    isKey = false,
                                                                                                                                    isOpen = false,
                                                                                                                                    name = "version",
                                                                                                                                    valueType = primitiveTypeIntNotNullable,
                                                                                                                                    withDefault = false)),
                                                                                                                                supertypes = listOf())),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "description",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = true),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "anotherVersion",
                                                                          valueType = primitiveTypeIntNotNullable,
                                                                          withDefault = true)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.FirstEntityWithPId",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.FirstEntityWithPIdData",
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
                                                                          name = "data",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = true,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "symbolicId",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.testEntities.entities.FirstPId",
                                                                                                                                properties = listOf(
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

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.GrandParentWithId",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.GrandParentWithIdData",
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
                                                                          name = "myId",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = true,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "symbolicId",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.testEntities.entities.GrandParentWithId\$GrandParentId",
                                                                                                                                properties = listOf(
                                                                                                                                  OwnPropertyMetadata(
                                                                                                                                    isComputable = false,
                                                                                                                                    isKey = false,
                                                                                                                                    isOpen = false,
                                                                                                                                    name = "id",
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

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.HeadAbstractionEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.HeadAbstractionEntityData",
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
                                                                          name = "data",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "child",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.CompositeBaseEntity",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = true),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = true,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "symbolicId",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.testEntities.entities.HeadAbstractionSymbolicId",
                                                                                                                                properties = listOf(
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

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.IntEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.IntEntityData",
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
                                                                          name = "data",
                                                                          valueType = primitiveTypeIntNotNullable,
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.KeyChild",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.KeyChildData",
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
                                                                          name = "data",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "parentEntity",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.KeyParent",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = false),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.KeyParent",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.KeyParentData",
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
                                                                          isKey = true,
                                                                          isOpen = false,
                                                                          name = "keyField",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "notKeyField",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "children",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.KeyChild",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = false),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.LeftEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.LeftEntityData",
                                  supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity",
                                                      "com.intellij.platform.workspace.storage.testEntities.entities.BaseEntity",
                                                      "com.intellij.platform.workspace.storage.testEntities.entities.CompositeBaseEntity"),
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
                                                                          name = "parentEntity",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.CompositeBaseEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = true),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "children",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.BaseEntity",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = false),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "parent",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.HeadAbstractionEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = true),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.LinkedListEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.LinkedListEntityData",
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
                                                                          name = "myName",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "next",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.testEntities.entities.LinkedListEntityId",
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
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = true,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "symbolicId",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.testEntities.entities.LinkedListEntityId",
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

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.ListEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.ListEntityData",
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
                                                                          name = "data",
                                                                          valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                                            primitiveTypeStringNotNullable),
                                                                                                                          primitive = primitiveTypeListNotNullable),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.ListVFUEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.ListVFUEntityData",
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
                                                                          name = "data",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "fileProperty",
                                                                          valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                                            ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                    typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                      fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl"))),
                                                                                                                          primitive = primitiveTypeListNotNullable),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.MainEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.MainEntityData",
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
                                                                          name = "x",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.MainEntityList",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.MainEntityListData",
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
                                                                          name = "x",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.MainEntityParentList",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.MainEntityParentListData",
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
                                                                          name = "x",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "children",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.AttachedEntityParentList",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = false),
                                                                          withDefault = false)),
                                  extProperties = listOf(ExtPropertyMetadata(isComputable = false,
                                                                             isOpen = false,
                                                                             name = "ref",
                                                                             receiverFqn = "com.intellij.platform.workspace.storage.testEntities.entities.AttachedEntityParentList",
                                                                             valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                           entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.MainEntityParentList",
                                                                                                                           isChild = false,
                                                                                                                           isNullable = true),
                                                                             withDefault = false)),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.MainEntityToParent",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.MainEntityToParentData",
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
                                                                          name = "x",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "child",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.AttachedEntityToParent",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = true),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "childNullableParent",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.AttachedEntityToNullableParent",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = true),
                                                                          withDefault = false)),
                                  extProperties = listOf(ExtPropertyMetadata(isComputable = false,
                                                                             isOpen = false,
                                                                             name = "nullableRef",
                                                                             receiverFqn = "com.intellij.platform.workspace.storage.testEntities.entities.AttachedEntityToNullableParent",
                                                                             valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                                                           entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.MainEntityToParent",
                                                                                                                           isChild = false,
                                                                                                                           isNullable = true),
                                                                             withDefault = false),
                                                         ExtPropertyMetadata(isComputable = false,
                                                                             isOpen = false,
                                                                             name = "ref",
                                                                             receiverFqn = "com.intellij.platform.workspace.storage.testEntities.entities.AttachedEntityToParent",
                                                                             valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                                                           entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.MainEntityToParent",
                                                                                                                           isChild = false,
                                                                                                                           isNullable = false),
                                                                             withDefault = false)),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.MiddleEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.MiddleEntityData",
                                  supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity",
                                                      "com.intellij.platform.workspace.storage.testEntities.entities.BaseEntity"),
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
                                                                          name = "parentEntity",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.CompositeBaseEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = true),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "property",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.ModuleTestEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.ModuleTestEntityData",
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
                                                                          name = "contentRoots",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.ContentRootTestEntity",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = false),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "facets",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.FacetTestEntity",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = false),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = true,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "symbolicId",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.testEntities.entities.ModuleTestEntitySymbolicId",
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

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.NamedChildEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.NamedChildEntityData",
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
                                                                          name = "childProperty",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "parentEntity",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.NamedEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = false),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.NamedEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.NamedEntityData",
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
                                                                          name = "myName",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "additionalProperty",
                                                                          valueType = primitiveTypeStringNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "children",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.NamedChildEntity",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = false),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = true,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "symbolicId",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.testEntities.entities.NameId",
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

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.NullableVFUEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.NullableVFUEntityData",
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
                                                                          name = "data",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "fileProperty",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = true,
                                                                                                                              typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.OneEntityWithSymbolicId",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.OneEntityWithSymbolicIdData",
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
                                                                          name = "myName",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = true,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "symbolicId",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.testEntities.entities.OneSymbolicId",
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

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.OoChildAlsoWithPidEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.OoChildAlsoWithPidEntityData",
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
                                                                          name = "childProperty",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "parentEntity",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.OoParentWithPidEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = false),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = true,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "symbolicId",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.testEntities.entities.OoChildEntityId",
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

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.OoChildEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.OoChildEntityData",
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
                                                                          name = "childProperty",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "parentEntity",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.OoParentEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = false),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.OoChildForParentWithPidEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.OoChildForParentWithPidEntityData",
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
                                                                          name = "childProperty",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "parentEntity",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.OoParentWithPidEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = false),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.OoChildWithNullableParentEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.OoChildWithNullableParentEntityData",
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
                                                                          name = "parentEntity",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.OoParentEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = true),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.OoChildWithPidEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.OoChildWithPidEntityData",
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
                                                                          name = "childProperty",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "parentEntity",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.OoParentWithoutPidEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = false),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = true,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "symbolicId",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.testEntities.entities.OoChildEntityId",
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

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.OoParentEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.OoParentEntityData",
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
                                                                          name = "parentProperty",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "child",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.OoChildEntity",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = true),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "anotherChild",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.OoChildWithNullableParentEntity",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = true),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.OoParentWithPidEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.OoParentWithPidEntityData",
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
                                                                          name = "parentProperty",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = true,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "symbolicId",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.testEntities.entities.OoParentEntityId",
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
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "childOne",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.OoChildForParentWithPidEntity",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = true),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "childThree",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.OoChildAlsoWithPidEntity",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = true),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.OoParentWithoutPidEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.OoParentWithoutPidEntityData",
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
                                                                          name = "parentProperty",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "childOne",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.OoChildWithPidEntity",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = true),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.OptionalIntEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.OptionalIntEntityData",
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
                                                                          name = "data",
                                                                          valueType = primitiveTypeIntNullable,
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.OptionalOneToOneChildEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.OptionalOneToOneChildEntityData",
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
                                                                          name = "data",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "parent",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.OptionalOneToOneParentEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = true),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.OptionalOneToOneParentEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.OptionalOneToOneParentEntityData",
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
                                                                          name = "child",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.OptionalOneToOneChildEntity",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = true),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.OptionalStringEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.OptionalStringEntityData",
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
                                                                          name = "data",
                                                                          valueType = primitiveTypeStringNullable,
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.ParentAbEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.ParentAbEntityData",
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
                                                                          name = "children",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.ChildAbstractBaseEntity",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = false),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.ParentChainEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.ParentChainEntityData",
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
                                                                          name = "root",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.CompositeAbstractEntity",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = true),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.ParentEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.ParentEntityData",
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
                                                                          name = "parentData",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "child",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.ChildEntity",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = true),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.ParentEntityWithSymbolicId",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.ParentEntityWithSymbolicIdData",
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
                                                                          name = "myName",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "children",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.ChildEntityWithSymbolicId",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = false),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = true,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "symbolicId",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.testEntities.entities.ParentNameId",
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

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.ParentMultipleEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.ParentMultipleEntityData",
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
                                                                          name = "parentData",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "children",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.ChildMultipleEntity",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = false),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.ParentSingleAbEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.ParentSingleAbEntityData",
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
                                                                          name = "child",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.ChildSingleAbstractBaseEntity",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = true),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.ParentSubEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.ParentSubEntityData",
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
                                                                          name = "parentData",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "child",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.ChildSubEntity",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = true),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.ParentWithExtensionEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.ParentWithExtensionEntityData",
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
                                                                          name = "data",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.ParentWithId",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.ParentWithIdData",
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
                                                                          name = "myId",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "parent",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.GrandParentWithId",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = false),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "children",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.ChildWithId",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = false),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = true,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "symbolicId",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.testEntities.entities.ParentWithId\$ParentId",
                                                                                                                                properties = listOf(
                                                                                                                                  OwnPropertyMetadata(
                                                                                                                                    isComputable = false,
                                                                                                                                    isKey = false,
                                                                                                                                    isOpen = false,
                                                                                                                                    name = "id",
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
                                  extProperties = listOf(ExtPropertyMetadata(isComputable = false,
                                                                             isOpen = false,
                                                                             name = "children",
                                                                             receiverFqn = "com.intellij.platform.workspace.storage.testEntities.entities.GrandParentWithId",
                                                                             valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                           entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.ParentWithId",
                                                                                                                           isChild = true,
                                                                                                                           isNullable = false),
                                                                             withDefault = false)),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.ParentWithLinkToAbstractChild",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.ParentWithLinkToAbstractChildData",
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
                                                                          name = "data",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "child",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.AbstractChildWithLinkToParentEntity",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = true),
                                                                          withDefault = false)),
                                  extProperties = listOf(ExtPropertyMetadata(isComputable = false,
                                                                             isOpen = false,
                                                                             name = "parent",
                                                                             receiverFqn = "com.intellij.platform.workspace.storage.testEntities.entities.AbstractChildWithLinkToParentEntity",
                                                                             valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE,
                                                                                                                           entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.ParentWithLinkToAbstractChild",
                                                                                                                           isChild = false,
                                                                                                                           isNullable = true),
                                                                             withDefault = false)),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.ParentWithNulls",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.ParentWithNullsData",
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
                                                                          name = "parentData",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "child",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.ChildWithNulls",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = true),
                                                                          withDefault = false)),
                                  extProperties = listOf(ExtPropertyMetadata(isComputable = false,
                                                                             isOpen = false,
                                                                             name = "parentEntity",
                                                                             receiverFqn = "com.intellij.platform.workspace.storage.testEntities.entities.ChildWithNulls",
                                                                             valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                                                           entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.ParentWithNulls",
                                                                                                                           isChild = false,
                                                                                                                           isNullable = true),
                                                                             withDefault = false)),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.ParentWithNullsMultiple",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.ParentWithNullsMultipleData",
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
                                                                          name = "parentData",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "children",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.ChildWithNullsMultiple",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = false),
                                                                          withDefault = false)),
                                  extProperties = listOf(ExtPropertyMetadata(isComputable = false,
                                                                             isOpen = false,
                                                                             name = "parent",
                                                                             receiverFqn = "com.intellij.platform.workspace.storage.testEntities.entities.ChildWithNullsMultiple",
                                                                             valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                           entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.ParentWithNullsMultiple",
                                                                                                                           isChild = false,
                                                                                                                           isNullable = true),
                                                                             withDefault = false)),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.ParentWithNullsOppositeMultiple",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.ParentWithNullsOppositeMultipleData",
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
                                                                          name = "parentData",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.PlaceholderEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.PlaceholderEntityData",
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
                                                                          name = "myId",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.ProjectModelTestEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.ProjectModelTestEntityData",
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
                                                                          name = "info",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "descriptor",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.testEntities.entities.Descriptor",
                                                                                                                                subclasses = listOf(
                                                                                                                                  FinalClassMetadata.ClassMetadata(
                                                                                                                                    fqName = "com.intellij.platform.workspace.storage.testEntities.entities.DescriptorInstance",
                                                                                                                                    properties = listOf(
                                                                                                                                      OwnPropertyMetadata(
                                                                                                                                        isComputable = false,
                                                                                                                                        isKey = false,
                                                                                                                                        isOpen = false,
                                                                                                                                        name = "data",
                                                                                                                                        valueType = primitiveTypeStringNotNullable,
                                                                                                                                        withDefault = false)),
                                                                                                                                    supertypes = listOf(
                                                                                                                                      "com.intellij.platform.workspace.storage.testEntities.entities.Descriptor"))),
                                                                                                                                supertypes = listOf())),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "parentEntity",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.ProjectModelTestEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = true),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "childrenEntities",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.ProjectModelTestEntity",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = false),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "contentRoot",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.ContentRootTestEntity",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = true),
                                                                          withDefault = false)),
                                  extProperties = listOf(ExtPropertyMetadata(isComputable = false,
                                                                             isOpen = false,
                                                                             name = "projectModelTestEntity",
                                                                             receiverFqn = "com.intellij.platform.workspace.storage.testEntities.entities.ContentRootTestEntity",
                                                                             valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                                                           entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.ProjectModelTestEntity",
                                                                                                                           isChild = false,
                                                                                                                           isNullable = true),
                                                                             withDefault = false)),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.RightEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.RightEntityData",
                                  supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity",
                                                      "com.intellij.platform.workspace.storage.testEntities.entities.BaseEntity",
                                                      "com.intellij.platform.workspace.storage.testEntities.entities.CompositeBaseEntity"),
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
                                                                          name = "parentEntity",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.CompositeBaseEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = true),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "children",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.BaseEntity",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = false),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "parent",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.HeadAbstractionEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = true),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.SampleEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.SampleEntityData",
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
                                                                          name = "booleanProperty",
                                                                          valueType = primitiveTypeBooleanNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "stringProperty",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "stringListProperty",
                                                                          valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                                            primitiveTypeStringNotNullable),
                                                                                                                          primitive = primitiveTypeListNotNullable),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "stringMapProperty",
                                                                          valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                                            primitiveTypeStringNotNullable,
                                                                            primitiveTypeStringNotNullable),
                                                                                                                          primitive = primitiveTypeMapNotNullable),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "fileProperty",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "children",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.ChildSampleEntity",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = false),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "nullableData",
                                                                          valueType = primitiveTypeStringNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "randomUUID",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = true,
                                                                                                                              typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                                fqName = "java.util.UUID")),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.SampleEntity2",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.SampleEntity2Data",
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
                                                                          name = "data",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "boolData",
                                                                          valueType = primitiveTypeBooleanNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "optionalData",
                                                                          valueType = primitiveTypeStringNullable,
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.SampleWithSymbolicIdEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.SampleWithSymbolicIdEntityData",
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
                                                                          name = "booleanProperty",
                                                                          valueType = primitiveTypeBooleanNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "stringProperty",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "stringListProperty",
                                                                          valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                                            primitiveTypeStringNotNullable),
                                                                                                                          primitive = primitiveTypeListNotNullable),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "stringMapProperty",
                                                                          valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                                            primitiveTypeStringNotNullable,
                                                                            primitiveTypeStringNotNullable),
                                                                                                                          primitive = primitiveTypeMapNotNullable),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "fileProperty",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "children",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.ChildWpidSampleEntity",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = false),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "nullableData",
                                                                          valueType = primitiveTypeStringNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = true,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "symbolicId",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.testEntities.entities.SampleSymbolicId",
                                                                                                                                properties = listOf(
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
                                                                                                                                    name = "stringProperty",
                                                                                                                                    valueType = primitiveTypeStringNotNullable,
                                                                                                                                    withDefault = false)),
                                                                                                                                supertypes = listOf(
                                                                                                                                  "com.intellij.platform.workspace.storage.SymbolicEntityId"))),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.SecondEntityWithPId",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.SecondEntityWithPIdData",
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
                                                                          name = "data",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = true,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "symbolicId",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.testEntities.entities.SecondPId",
                                                                                                                                properties = listOf(
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

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.SecondSampleEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.SecondSampleEntityData",
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
                                                                          name = "intProperty",
                                                                          valueType = primitiveTypeIntNotNullable,
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.SelfLinkedEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.SelfLinkedEntityData",
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
                                                                          name = "parentEntity",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.SelfLinkedEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = true),
                                                                          withDefault = false)),
                                  extProperties = listOf(ExtPropertyMetadata(isComputable = false,
                                                                             isOpen = false,
                                                                             name = "children",
                                                                             receiverFqn = "com.intellij.platform.workspace.storage.testEntities.entities.SelfLinkedEntity",
                                                                             valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                           entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.SelfLinkedEntity",
                                                                                                                           isChild = true,
                                                                                                                           isNullable = false),
                                                                             withDefault = false)),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.SetVFUEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.SetVFUEntityData",
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
                                                                          name = "data",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "fileProperty",
                                                                          valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                                            ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                    typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                      fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl"))),
                                                                                                                          primitive = primitiveTypeSetNotNullable),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.SimpleAbstractEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.SimpleAbstractEntityData",
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
                                                                          name = "parentInList",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.CompositeAbstractEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = true),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = true)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.SimpleChildAbstractEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.SimpleChildAbstractEntityData",
                                  supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity",
                                                      "com.intellij.platform.workspace.storage.testEntities.entities.SimpleAbstractEntity"),
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
                                                                          name = "parentInList",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.CompositeAbstractEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = true),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.SoftLinkReferencedChild",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.SoftLinkReferencedChildData",
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
                                                                          name = "parentEntity",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.EntityWithSoftLinks",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = false),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.SourceEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.SourceEntityData",
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
                                                                          name = "data",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "children",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.ChildSourceEntity",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = false),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.SourceRootTestEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.SourceRootTestEntityData",
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
                                                                          name = "data",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "contentRoot",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.ContentRootTestEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = false),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.SourceRootTestOrderEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.SourceRootTestOrderEntityData",
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
                                                                          name = "data",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "contentRoot",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.ContentRootTestEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = false),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.SpecificChildEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.SpecificChildEntityData",
                                  supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity",
                                                      "com.intellij.platform.workspace.storage.testEntities.entities.AbstractChildEntity"),
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
                                                                          name = "data",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "parent",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.ParentWithExtensionEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = false),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata =
      EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.SpecificChildWithLinkToParentEntity",
                     entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.SpecificChildWithLinkToParentEntityData",
                     supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity",
                                         "com.intellij.platform.workspace.storage.testEntities.entities.AbstractChildWithLinkToParentEntity"),
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
                                                             name = "data",
                                                             valueType = primitiveTypeStringNotNullable,
                                                             withDefault = false)),
                     extProperties = listOf(),
                     isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.SpecificParent",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.SpecificParentData",
                                  supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity",
                                                      "com.intellij.platform.workspace.storage.testEntities.entities.AbstractParentEntity"),
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
                                                                          name = "data",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "child",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.ChildWithExtensionParent",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = true),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.StringEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.StringEntityData",
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
                                                                          name = "data",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.SymbolicIdEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.SymbolicIdEntityData",
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
                                                                          name = "data",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = true,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "symbolicId",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.testEntities.entities.LinkedListEntityId",
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

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.TreeEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.TreeEntityData",
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
                                                                          name = "data",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "children",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.TreeEntity",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = false),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "parentEntity",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.TreeEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = true),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.TreeMultiparentLeafEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.TreeMultiparentLeafEntityData",
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
                                                                          name = "data",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "mainParent",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.TreeMultiparentRootEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = true),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "leafParent",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.TreeMultiparentLeafEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = true),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "children",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.TreeMultiparentLeafEntity",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = false),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.TreeMultiparentRootEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.TreeMultiparentRootEntityData",
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
                                                                          name = "data",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "children",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.TreeMultiparentLeafEntity",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = false),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = true,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "symbolicId",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.testEntities.entities.TreeMultiparentSymbolicId",
                                                                                                                                properties = listOf(
                                                                                                                                  OwnPropertyMetadata(
                                                                                                                                    isComputable = false,
                                                                                                                                    isKey = false,
                                                                                                                                    isOpen = false,
                                                                                                                                    name = "data",
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

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.UnknownFieldEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.UnknownFieldEntityData",
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
                                                                          name = "data",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                                fqName = "java.util.Date")),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.VFUEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.VFUEntityData",
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
                                                                          name = "data",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "fileProperty",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.VFUEntity2",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.VFUEntity2Data",
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
                                                                          name = "data",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "filePath",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = true,
                                                                                                                              typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "directoryPath",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "notNullRoots",
                                                                          valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                                            ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                    typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                      fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl"))),
                                                                                                                          primitive = primitiveTypeListNotNullable),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.VFUWithTwoPropertiesEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.VFUWithTwoPropertiesEntityData",
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
                                                                          name = "data",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "fileProperty",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "secondFileProperty",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.WithListSoftLinksEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.WithListSoftLinksEntityData",
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
                                                                          name = "myName",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "links",
                                                                          valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                                            ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                    typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                      fqName = "com.intellij.platform.workspace.storage.testEntities.entities.NameId",
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
                                                                                                                      supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId")))),
                                                                                                                          primitive = primitiveTypeListNotNullable),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = true,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "symbolicId",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.testEntities.entities.AnotherNameId",
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

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.WithSealedEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.WithSealedEntityData",
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
                                                                          name = "classes",
                                                                          valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                                            ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                    typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(
                                                                                                                      fqName = "com.intellij.platform.workspace.storage.testEntities.entities.MySealedClass",
                                                                                                                      subclasses = listOf(
                                                                                                                        FinalClassMetadata.ClassMetadata(
                                                                                                                          fqName = "com.intellij.platform.workspace.storage.testEntities.entities.MySealedClassOne",
                                                                                                                          properties = listOf(
                                                                                                                            OwnPropertyMetadata(
                                                                                                                              isComputable = false,
                                                                                                                              isKey = false,
                                                                                                                              isOpen = false,
                                                                                                                              name = "info",
                                                                                                                              valueType = primitiveTypeStringNotNullable,
                                                                                                                              withDefault = false)),
                                                                                                                          supertypes = listOf(
                                                                                                                            "com.intellij.platform.workspace.storage.testEntities.entities.MySealedClass")),
                                                                                                                        FinalClassMetadata.ClassMetadata(
                                                                                                                          fqName = "com.intellij.platform.workspace.storage.testEntities.entities.MySealedClassTwo",
                                                                                                                          properties = listOf(
                                                                                                                            OwnPropertyMetadata(
                                                                                                                              isComputable = false,
                                                                                                                              isKey = false,
                                                                                                                              isOpen = false,
                                                                                                                              name = "info",
                                                                                                                              valueType = primitiveTypeStringNotNullable,
                                                                                                                              withDefault = false)),
                                                                                                                          supertypes = listOf(
                                                                                                                            "com.intellij.platform.workspace.storage.testEntities.entities.MySealedClass"))),
                                                                                                                      supertypes = listOf()))),
                                                                                                                          primitive = primitiveTypeListNotNullable),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "interfaces",
                                                                          valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                                            ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                    typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(
                                                                                                                      fqName = "com.intellij.platform.workspace.storage.testEntities.entities.MySealedInterface",
                                                                                                                      subclasses = listOf(
                                                                                                                        FinalClassMetadata.ClassMetadata(
                                                                                                                          fqName = "com.intellij.platform.workspace.storage.testEntities.entities.MySealedInterfaceTwo",
                                                                                                                          properties = listOf(
                                                                                                                            OwnPropertyMetadata(
                                                                                                                              isComputable = false,
                                                                                                                              isKey = false,
                                                                                                                              isOpen = false,
                                                                                                                              name = "info",
                                                                                                                              valueType = primitiveTypeStringNotNullable,
                                                                                                                              withDefault = false)),
                                                                                                                          supertypes = listOf(
                                                                                                                            "com.intellij.platform.workspace.storage.testEntities.entities.MySealedInterface")),
                                                                                                                        FinalClassMetadata.ClassMetadata(
                                                                                                                          fqName = "com.intellij.platform.workspace.storage.testEntities.entities.MySealedInterfaceOne",
                                                                                                                          properties = listOf(
                                                                                                                            OwnPropertyMetadata(
                                                                                                                              isComputable = false,
                                                                                                                              isKey = false,
                                                                                                                              isOpen = false,
                                                                                                                              name = "info",
                                                                                                                              valueType = primitiveTypeStringNotNullable,
                                                                                                                              withDefault = false)),
                                                                                                                          supertypes = listOf(
                                                                                                                            "com.intellij.platform.workspace.storage.testEntities.entities.MySealedInterface"))),
                                                                                                                      supertypes = listOf()))),
                                                                                                                          primitive = primitiveTypeListNotNullable),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.WithSoftLinkEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.WithSoftLinkEntityData",
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
                                                                          name = "link",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.testEntities.entities.NameId",
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

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.XChildChildEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.XChildChildEntityData",
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
                                                                          name = "parent1",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.XParentEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = false),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "parent2",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.XChildEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = false),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.XChildEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.XChildEntityData",
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
                                                                          name = "childProperty",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "dataClass",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = true,
                                                                                                                              typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.testEntities.entities.DataClassX",
                                                                                                                                properties = listOf(
                                                                                                                                  OwnPropertyMetadata(
                                                                                                                                    isComputable = false,
                                                                                                                                    isKey = false,
                                                                                                                                    isOpen = false,
                                                                                                                                    name = "parent",
                                                                                                                                    valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                                                                      isNullable = false,
                                                                                                                                      typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                                        fqName = "com.intellij.platform.workspace.storage.EntityPointer")),
                                                                                                                                    withDefault = false),
                                                                                                                                  OwnPropertyMetadata(
                                                                                                                                    isComputable = false,
                                                                                                                                    isKey = false,
                                                                                                                                    isOpen = false,
                                                                                                                                    name = "stringProperty",
                                                                                                                                    valueType = primitiveTypeStringNotNullable,
                                                                                                                                    withDefault = false)),
                                                                                                                                supertypes = listOf())),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "parentEntity",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.XParentEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = false),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "childChild",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.XChildChildEntity",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = false),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.XChildWithOptionalParentEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.XChildWithOptionalParentEntityData",
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
                                                                          name = "childProperty",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "optionalParent",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.XParentEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = true),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.XParentEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.impl.XParentEntityData",
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
                                                                          name = "parentProperty",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "children",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.XChildEntity",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = false),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "optionalChildren",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.XChildWithOptionalParentEntity",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = false),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "childChild",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.XChildChildEntity",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = false),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata =
      EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.AnotherOneToManyRefEntity",
                     entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.impl.AnotherOneToManyRefEntityData",
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
                                                             name = "parentEntity",
                                                             valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                           entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.OneToManyRefEntity",
                                                                                                           isChild = false,
                                                                                                           isNullable = false),
                                                             withDefault = false),
                                         OwnPropertyMetadata(isComputable = false,
                                                             isKey = false,
                                                             isOpen = false,
                                                             name = "version",
                                                             valueType = primitiveTypeIntNotNullable,
                                                             withDefault = false),
                                         OwnPropertyMetadata(isComputable = false,
                                                             isKey = false,
                                                             isOpen = false,
                                                             name = "someData",
                                                             valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                 typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                   fqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.OneToManyRefDataClass",
                                                                                                                   properties = listOf(
                                                                                                                     OwnPropertyMetadata(
                                                                                                                       isComputable = false,
                                                                                                                       isKey = false,
                                                                                                                       isOpen = false,
                                                                                                                       name = "list",
                                                                                                                       valueType = ValueTypeMetadata.ParameterizedType(
                                                                                                                         generics = listOf(
                                                                                                                           ValueTypeMetadata.ParameterizedType(
                                                                                                                             generics = listOf(
                                                                                                                               primitiveTypeStringNotNullable),
                                                                                                                             primitive = primitiveTypeSetNotNullable)),
                                                                                                                         primitive = primitiveTypeListNotNullable),
                                                                                                                       withDefault = false),
                                                                                                                     OwnPropertyMetadata(
                                                                                                                       isComputable = false,
                                                                                                                       isKey = false,
                                                                                                                       isOpen = false,
                                                                                                                       name = "value",
                                                                                                                       valueType = primitiveTypeIntNotNullable,
                                                                                                                       withDefault = false)),
                                                                                                                   supertypes = listOf())),
                                                             withDefault = false)),
                     extProperties = listOf(),
                     isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata =
      EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.AnotherOneToOneRefEntity",
                     entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.impl.AnotherOneToOneRefEntityData",
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
                                                             name = "someString",
                                                             valueType = primitiveTypeStringNotNullable,
                                                             withDefault = false),
                                         OwnPropertyMetadata(isComputable = false,
                                                             isKey = false,
                                                             isOpen = false,
                                                             name = "boolean",
                                                             valueType = primitiveTypeBooleanNotNullable,
                                                             withDefault = false),
                                         OwnPropertyMetadata(isComputable = false,
                                                             isKey = false,
                                                             isOpen = false,
                                                             name = "parentEntity",
                                                             valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                                           entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.OneToOneRefEntity",
                                                                                                           isChild = false,
                                                                                                           isNullable = false),
                                                             withDefault = false)),
                     extProperties = listOf(),
                     isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata =
      EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ChangedComputablePropEntity",
                     entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.impl.ChangedComputablePropEntityData",
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
                                                             name = "text",
                                                             valueType = primitiveTypeStringNotNullable,
                                                             withDefault = false),
                                         OwnPropertyMetadata(isComputable = true,
                                                             isKey = false,
                                                             isOpen = false,
                                                             name = "symbolicId",
                                                             valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                 typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                   fqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ChangedComputablePropEntityId",
                                                                                                                   properties = listOf(
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
                                                                                                                       name = "text",
                                                                                                                       valueType = primitiveTypeStringNotNullable,
                                                                                                                       withDefault = false)),
                                                                                                                   supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId"))),
                                                             withDefault = false)),
                     extProperties = listOf(),
                     isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata =
      EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ChangedComputablePropsOrderEntity",
                     entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.impl.ChangedComputablePropsOrderEntityData",
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
                                                             name = "someKey",
                                                             valueType = primitiveTypeIntNotNullable,
                                                             withDefault = false),
                                         OwnPropertyMetadata(isComputable = false,
                                                             isKey = false,
                                                             isOpen = false,
                                                             name = "names",
                                                             valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                               primitiveTypeStringNotNullable), primitive = primitiveTypeListNotNullable),
                                                             withDefault = false),
                                         OwnPropertyMetadata(isComputable = true,
                                                             isKey = false,
                                                             isOpen = false,
                                                             name = "symbolicId",
                                                             valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                 typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                   fqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ChangedComputablePropsOrderEntityId",
                                                                                                                   properties = listOf(
                                                                                                                     OwnPropertyMetadata(
                                                                                                                       isComputable = false,
                                                                                                                       isKey = false,
                                                                                                                       isOpen = false,
                                                                                                                       name = "names",
                                                                                                                       valueType = ValueTypeMetadata.ParameterizedType(
                                                                                                                         generics = listOf(
                                                                                                                           primitiveTypeStringNotNullable),
                                                                                                                         primitive = primitiveTypeListNotNullable),
                                                                                                                       withDefault = false),
                                                                                                                     OwnPropertyMetadata(
                                                                                                                       isComputable = false,
                                                                                                                       isKey = false,
                                                                                                                       isOpen = false,
                                                                                                                       name = "presentableName",
                                                                                                                       valueType = primitiveTypeStringNotNullable,
                                                                                                                       withDefault = false)),
                                                                                                                   supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId"))),
                                                             withDefault = false),
                                         OwnPropertyMetadata(isComputable = false,
                                                             isKey = false,
                                                             isOpen = false,
                                                             name = "value",
                                                             valueType = primitiveTypeIntNotNullable,
                                                             withDefault = false)),
                     extProperties = listOf(),
                     isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata =
      EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ChangedEnumNameEntity",
                     entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.impl.ChangedEnumNameEntityData",
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
                                                             name = "someEnum",
                                                             valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                 typeMetadata = FinalClassMetadata.EnumClassMetadata(
                                                                                                                   fqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ChangedEnumNameEnum",
                                                                                                                   properties = listOf(),
                                                                                                                   supertypes = listOf("java.io.Serializable",
                                                                                                                                       "kotlin.Comparable",
                                                                                                                                       "kotlin.Enum"),
                                                                                                                   values = listOf("A_ENTRY",
                                                                                                                                   "B_ENTRY",
                                                                                                                                   "CA_ENTRY"))),
                                                             withDefault = false)),
                     extProperties = listOf(),
                     isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata =
      EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ChangedPropsOrderEntity",
                     entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.impl.ChangedPropsOrderEntityData",
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
                                                             name = "version",
                                                             valueType = primitiveTypeIntNotNullable,
                                                             withDefault = false),
                                         OwnPropertyMetadata(isComputable = false,
                                                             isKey = false,
                                                             isOpen = false,
                                                             name = "string",
                                                             valueType = primitiveTypeStringNotNullable,
                                                             withDefault = false),
                                         OwnPropertyMetadata(isComputable = false,
                                                             isKey = false,
                                                             isOpen = false,
                                                             name = "list",
                                                             valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                               ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                                 primitiveTypeIntNotNullable), primitive = primitiveTypeSetNotNullable)),
                                                                                                             primitive = primitiveTypeListNotNullable),
                                                             withDefault = false),
                                         OwnPropertyMetadata(isComputable = false,
                                                             isKey = false,
                                                             isOpen = false,
                                                             name = "data",
                                                             valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                 typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                   fqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ChangedPropsOrderDataClass",
                                                                                                                   properties = listOf(
                                                                                                                     OwnPropertyMetadata(
                                                                                                                       isComputable = false,
                                                                                                                       isKey = false,
                                                                                                                       isOpen = false,
                                                                                                                       name = "text",
                                                                                                                       valueType = primitiveTypeStringNotNullable,
                                                                                                                       withDefault = false),
                                                                                                                     OwnPropertyMetadata(
                                                                                                                       isComputable = false,
                                                                                                                       isKey = false,
                                                                                                                       isOpen = false,
                                                                                                                       name = "value",
                                                                                                                       valueType = primitiveTypeIntNotNullable,
                                                                                                                       withDefault = false)),
                                                                                                                   supertypes = listOf())),
                                                             withDefault = false)),
                     extProperties = listOf(),
                     isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata =
      EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ChangedValueTypeEntity",
                     entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.impl.ChangedValueTypeEntityData",
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
                                                             name = "type",
                                                             valueType = primitiveTypeStringNotNullable,
                                                             withDefault = false),
                                         OwnPropertyMetadata(isComputable = false,
                                                             isKey = false,
                                                             isOpen = false,
                                                             name = "someKey",
                                                             valueType = primitiveTypeIntNotNullable,
                                                             withDefault = false),
                                         OwnPropertyMetadata(isComputable = false,
                                                             isKey = false,
                                                             isOpen = false,
                                                             name = "text",
                                                             valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                               primitiveTypeStringNotNullable), primitive = primitiveTypeListNotNullable),
                                                             withDefault = false)),
                     extProperties = listOf(),
                     isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata =
      EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ComputablePropEntity",
                     entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.impl.ComputablePropEntityData",
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
                                                             name = "list",
                                                             valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                               ValueTypeMetadata.ParameterizedType(generics = listOf(ValueTypeMetadata.ParameterizedType(
                                                                 generics = listOf(primitiveTypeIntNullable),
                                                                 primitive = primitiveTypeListNotNullable), primitiveTypeStringNotNullable),
                                                                                                   primitive = primitiveTypeMapNotNullable)),
                                                                                                             primitive = primitiveTypeListNotNullable),
                                                             withDefault = false),
                                         OwnPropertyMetadata(isComputable = false,
                                                             isKey = false,
                                                             isOpen = false,
                                                             name = "value",
                                                             valueType = primitiveTypeIntNotNullable,
                                                             withDefault = false)),
                     extProperties = listOf(),
                     isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.DefaultPropEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.impl.DefaultPropEntityData",
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
                                                                          name = "someString",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "someList",
                                                                          valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                                            primitiveTypeIntNotNullable),
                                                                                                                          primitive = primitiveTypeListNotNullable),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "constInt",
                                                                          valueType = primitiveTypeIntNotNullable,
                                                                          withDefault = true)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.EnumPropsEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.impl.EnumPropsEntityData",
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
                                                                          name = "someEnum",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.EnumClassMetadata(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.EnumPropsEnum",
                                                                                                                                properties = listOf(
                                                                                                                                  OwnPropertyMetadata(
                                                                                                                                    isComputable = false,
                                                                                                                                    isKey = false,
                                                                                                                                    isOpen = false,
                                                                                                                                    name = "value",
                                                                                                                                    valueType = primitiveTypeIntNotNullable,
                                                                                                                                    withDefault = false)),
                                                                                                                                supertypes = listOf(
                                                                                                                                  "java.io.Serializable",
                                                                                                                                  "kotlin.Comparable",
                                                                                                                                  "kotlin.Enum"),
                                                                                                                                values = listOf(
                                                                                                                                  "FIRST",
                                                                                                                                  "SECOND",
                                                                                                                                  "THIRD"))),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.KeyPropEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.impl.KeyPropEntityData",
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
                                                                          name = "someInt",
                                                                          valueType = primitiveTypeIntNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "text",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = true,
                                                                          isOpen = false,
                                                                          name = "url",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.NotNullToNullEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.impl.NotNullToNullEntityData",
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
                                                                          name = "nullInt",
                                                                          valueType = primitiveTypeIntNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "notNullString",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "notNullList",
                                                                          valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                                            primitiveTypeIntNotNullable),
                                                                                                                          primitive = primitiveTypeListNotNullable),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.NullToNotNullEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.impl.NullToNotNullEntityData",
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
                                                                          name = "nullString",
                                                                          valueType = primitiveTypeStringNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "notNullBoolean",
                                                                          valueType = primitiveTypeBooleanNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "notNullInt",
                                                                          valueType = primitiveTypeIntNotNullable,
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.OneToManyRefEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.impl.OneToManyRefEntityData",
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
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.OneToManyRefDataClass",
                                                                                                                                properties = listOf(
                                                                                                                                  OwnPropertyMetadata(
                                                                                                                                    isComputable = false,
                                                                                                                                    isKey = false,
                                                                                                                                    isOpen = false,
                                                                                                                                    name = "list",
                                                                                                                                    valueType = ValueTypeMetadata.ParameterizedType(
                                                                                                                                      generics = listOf(
                                                                                                                                        ValueTypeMetadata.ParameterizedType(
                                                                                                                                          generics = listOf(
                                                                                                                                            primitiveTypeStringNotNullable),
                                                                                                                                          primitive = primitiveTypeSetNotNullable)),
                                                                                                                                      primitive = primitiveTypeListNotNullable),
                                                                                                                                    withDefault = false),
                                                                                                                                  OwnPropertyMetadata(
                                                                                                                                    isComputable = false,
                                                                                                                                    isKey = false,
                                                                                                                                    isOpen = false,
                                                                                                                                    name = "value",
                                                                                                                                    valueType = primitiveTypeIntNotNullable,
                                                                                                                                    withDefault = false)),
                                                                                                                                supertypes = listOf())),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "anotherEntity",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.AnotherOneToManyRefEntity",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = false),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.OneToOneRefEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.impl.OneToOneRefEntityData",
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
                                                                          name = "version",
                                                                          valueType = primitiveTypeIntNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "text",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "anotherEntity",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.AnotherOneToOneRefEntity",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = true),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SimpleObjectsEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.impl.SimpleObjectsEntityData",
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
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SimpleObjectsSealedClass",
                                                                                                                                subclasses = listOf(
                                                                                                                                  FinalClassMetadata.ObjectMetadata(
                                                                                                                                    fqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SimpleObjectsSealedClass\$SecondSimpleObjectsSealedClassObject",
                                                                                                                                    properties = listOf(
                                                                                                                                      OwnPropertyMetadata(
                                                                                                                                        isComputable = false,
                                                                                                                                        isKey = false,
                                                                                                                                        isOpen = false,
                                                                                                                                        name = "data",
                                                                                                                                        valueType = primitiveTypeStringNotNullable,
                                                                                                                                        withDefault = false),
                                                                                                                                      OwnPropertyMetadata(
                                                                                                                                        isComputable = false,
                                                                                                                                        isKey = false,
                                                                                                                                        isOpen = false,
                                                                                                                                        name = "id",
                                                                                                                                        valueType = primitiveTypeIntNotNullable,
                                                                                                                                        withDefault = false),
                                                                                                                                      OwnPropertyMetadata(
                                                                                                                                        isComputable = false,
                                                                                                                                        isKey = false,
                                                                                                                                        isOpen = false,
                                                                                                                                        name = "list",
                                                                                                                                        valueType = ValueTypeMetadata.ParameterizedType(
                                                                                                                                          generics = listOf(
                                                                                                                                            primitiveTypeStringNotNullable),
                                                                                                                                          primitive = primitiveTypeListNotNullable),
                                                                                                                                        withDefault = false)),
                                                                                                                                    supertypes = listOf(
                                                                                                                                      "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SimpleObjectsSealedClass")),
                                                                                                                                  FinalClassMetadata.ObjectMetadata(
                                                                                                                                    fqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SimpleObjectsSealedClass\$FirstSimpleObjectsSealedClassObject",
                                                                                                                                    properties = listOf(
                                                                                                                                      OwnPropertyMetadata(
                                                                                                                                        isComputable = false,
                                                                                                                                        isKey = false,
                                                                                                                                        isOpen = false,
                                                                                                                                        name = "data",
                                                                                                                                        valueType = primitiveTypeStringNotNullable,
                                                                                                                                        withDefault = false),
                                                                                                                                      OwnPropertyMetadata(
                                                                                                                                        isComputable = false,
                                                                                                                                        isKey = false,
                                                                                                                                        isOpen = false,
                                                                                                                                        name = "id",
                                                                                                                                        valueType = primitiveTypeIntNotNullable,
                                                                                                                                        withDefault = false),
                                                                                                                                      OwnPropertyMetadata(
                                                                                                                                        isComputable = false,
                                                                                                                                        isKey = false,
                                                                                                                                        isOpen = false,
                                                                                                                                        name = "value",
                                                                                                                                        valueType = primitiveTypeIntNotNullable,
                                                                                                                                        withDefault = false)),
                                                                                                                                    supertypes = listOf(
                                                                                                                                      "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SimpleObjectsSealedClass"))),
                                                                                                                                supertypes = listOf())),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SimplePropsEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.impl.SimplePropsEntityData",
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
                                                                          name = "text",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "list",
                                                                          valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                                            primitiveTypeIntNotNullable),
                                                                                                                          primitive = primitiveTypeListNotNullable),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "set",
                                                                          valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                                            ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                                              primitiveTypeStringNotNullable),
                                                                                                                primitive = primitiveTypeListNotNullable)),
                                                                                                                          primitive = primitiveTypeSetNotNullable),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "map",
                                                                          valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                                            ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                                              primitiveTypeIntNotNullable),
                                                                                                                primitive = primitiveTypeSetNotNullable),
                                                                            ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                                              primitiveTypeStringNotNullable),
                                                                                                                primitive = primitiveTypeListNotNullable)),
                                                                                                                          primitive = primitiveTypeMapNotNullable),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "bool",
                                                                          valueType = primitiveTypeBooleanNotNullable,
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata =
      EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SimpleSealedClassEntity",
                     entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.impl.SimpleSealedClassEntityData",
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
                                                             name = "text",
                                                             valueType = primitiveTypeStringNotNullable,
                                                             withDefault = false),
                                         OwnPropertyMetadata(isComputable = false,
                                                             isKey = false,
                                                             isOpen = false,
                                                             name = "someData",
                                                             valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                 typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(
                                                                                                                   fqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SimpleSealedClass",
                                                                                                                   subclasses = listOf(
                                                                                                                     FinalClassMetadata.ClassMetadata(
                                                                                                                       fqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SimpleSealedClass\$SecondKeyPropDataClass",
                                                                                                                       properties = listOf(
                                                                                                                         OwnPropertyMetadata(
                                                                                                                           isComputable = false,
                                                                                                                           isKey = false,
                                                                                                                           isOpen = false,
                                                                                                                           name = "type",
                                                                                                                           valueType = primitiveTypeStringNotNullable,
                                                                                                                           withDefault = false),
                                                                                                                         OwnPropertyMetadata(
                                                                                                                           isComputable = false,
                                                                                                                           isKey = false,
                                                                                                                           isOpen = false,
                                                                                                                           name = "value",
                                                                                                                           valueType = primitiveTypeIntNotNullable,
                                                                                                                           withDefault = false)),
                                                                                                                       supertypes = listOf("com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SimpleSealedClass")),
                                                                                                                     FinalClassMetadata.ClassMetadata(
                                                                                                                       fqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SimpleSealedClass\$FirstKeyPropDataClass",
                                                                                                                       properties = listOf(
                                                                                                                         OwnPropertyMetadata(
                                                                                                                           isComputable = false,
                                                                                                                           isKey = false,
                                                                                                                           isOpen = false,
                                                                                                                           name = "text",
                                                                                                                           valueType = primitiveTypeStringNotNullable,
                                                                                                                           withDefault = false),
                                                                                                                         OwnPropertyMetadata(
                                                                                                                           isComputable = false,
                                                                                                                           isKey = false,
                                                                                                                           isOpen = false,
                                                                                                                           name = "type",
                                                                                                                           valueType = primitiveTypeStringNotNullable,
                                                                                                                           withDefault = false)),
                                                                                                                       supertypes = listOf("com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SimpleSealedClass"))),
                                                                                                                   supertypes = listOf())),
                                                             withDefault = false)),
                     extProperties = listOf(),
                     isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SubsetEnumEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.impl.SubsetEnumEntityData",
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
                                                                          name = "someEnum",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.EnumClassMetadata(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SubsetEnumEnum",
                                                                                                                                properties = listOf(
                                                                                                                                  OwnPropertyMetadata(
                                                                                                                                    isComputable = false,
                                                                                                                                    isKey = false,
                                                                                                                                    isOpen = false,
                                                                                                                                    name = "type",
                                                                                                                                    valueType = primitiveTypeStringNotNullable,
                                                                                                                                    withDefault = false)),
                                                                                                                                supertypes = listOf(
                                                                                                                                  "java.io.Serializable",
                                                                                                                                  "kotlin.Comparable",
                                                                                                                                  "kotlin.Enum"),
                                                                                                                                values = listOf(
                                                                                                                                  "A_ENUM",
                                                                                                                                  "C_ENUM",
                                                                                                                                  "E_ENUM"))),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata =
      EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SubsetSealedClassEntity",
                     entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.impl.SubsetSealedClassEntityData",
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
                                                             valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                 typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(
                                                                                                                   fqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SubsetSealedClass",
                                                                                                                   subclasses = listOf(
                                                                                                                     FinalClassMetadata.ClassMetadata(
                                                                                                                       fqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SubsetSealedClass\$SecondSubsetSealedClassDataClass",
                                                                                                                       properties = listOf(
                                                                                                                         OwnPropertyMetadata(
                                                                                                                           isComputable = false,
                                                                                                                           isKey = false,
                                                                                                                           isOpen = false,
                                                                                                                           name = "list",
                                                                                                                           valueType = ValueTypeMetadata.ParameterizedType(
                                                                                                                             generics = listOf(
                                                                                                                               primitiveTypeIntNotNullable),
                                                                                                                             primitive = primitiveTypeListNotNullable),
                                                                                                                           withDefault = false),
                                                                                                                         OwnPropertyMetadata(
                                                                                                                           isComputable = false,
                                                                                                                           isKey = false,
                                                                                                                           isOpen = false,
                                                                                                                           name = "name",
                                                                                                                           valueType = primitiveTypeStringNotNullable,
                                                                                                                           withDefault = false)),
                                                                                                                       supertypes = listOf("com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SubsetSealedClass")),
                                                                                                                     FinalClassMetadata.ClassMetadata(
                                                                                                                       fqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SubsetSealedClass\$FirstSubsetSealedClassDataClass",
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
                                                                                                                       supertypes = listOf("com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SubsetSealedClass")),
                                                                                                                     FinalClassMetadata.ObjectMetadata(
                                                                                                                       fqName = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SubsetSealedClass\$FirstSubsetSealedClassObject",
                                                                                                                       properties = listOf(
                                                                                                                         OwnPropertyMetadata(
                                                                                                                           isComputable = false,
                                                                                                                           isKey = false,
                                                                                                                           isOpen = false,
                                                                                                                           name = "name",
                                                                                                                           valueType = primitiveTypeStringNotNullable,
                                                                                                                           withDefault = false)),
                                                                                                                       supertypes = listOf("com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SubsetSealedClass"))),
                                                                                                                   supertypes = listOf())),
                                                             withDefault = false)),
                     extProperties = listOf(),
                     isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata =
      EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.AnotherOneToManyRefEntity",
                     entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.impl.AnotherOneToManyRefEntityData",
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
                                                             name = "parentEntity",
                                                             valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                                           entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.OneToManyRefEntity",
                                                                                                           isChild = false,
                                                                                                           isNullable = false),
                                                             withDefault = false),
                                         OwnPropertyMetadata(isComputable = false,
                                                             isKey = false,
                                                             isOpen = false,
                                                             name = "version",
                                                             valueType = primitiveTypeIntNotNullable,
                                                             withDefault = false),
                                         OwnPropertyMetadata(isComputable = false,
                                                             isKey = false,
                                                             isOpen = false,
                                                             name = "someData",
                                                             valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                 typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                   fqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.OneToManyRefDataClass",
                                                                                                                   properties = listOf(
                                                                                                                     OwnPropertyMetadata(
                                                                                                                       isComputable = false,
                                                                                                                       isKey = false,
                                                                                                                       isOpen = false,
                                                                                                                       name = "list",
                                                                                                                       valueType = ValueTypeMetadata.ParameterizedType(
                                                                                                                         generics = listOf(
                                                                                                                           ValueTypeMetadata.ParameterizedType(
                                                                                                                             generics = listOf(
                                                                                                                               primitiveTypeStringNotNullable),
                                                                                                                             primitive = primitiveTypeSetNotNullable)),
                                                                                                                         primitive = primitiveTypeListNotNullable),
                                                                                                                       withDefault = false),
                                                                                                                     OwnPropertyMetadata(
                                                                                                                       isComputable = false,
                                                                                                                       isKey = false,
                                                                                                                       isOpen = false,
                                                                                                                       name = "value",
                                                                                                                       valueType = primitiveTypeIntNotNullable,
                                                                                                                       withDefault = false)),
                                                                                                                   supertypes = listOf())),
                                                             withDefault = false)),
                     extProperties = listOf(),
                     isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata =
      EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.AnotherOneToOneRefEntity",
                     entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.impl.AnotherOneToOneRefEntityData",
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
                                                             name = "someString",
                                                             valueType = primitiveTypeStringNotNullable,
                                                             withDefault = false),
                                         OwnPropertyMetadata(isComputable = false,
                                                             isKey = false,
                                                             isOpen = false,
                                                             name = "boolean",
                                                             valueType = primitiveTypeBooleanNotNullable,
                                                             withDefault = false),
                                         OwnPropertyMetadata(isComputable = false,
                                                             isKey = false,
                                                             isOpen = false,
                                                             name = "parentEntity",
                                                             valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                           entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.OneToOneRefEntity",
                                                                                                           isChild = false,
                                                                                                           isNullable = false),
                                                             withDefault = false)),
                     extProperties = listOf(),
                     isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata =
      EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ChangedComputablePropEntity",
                     entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.impl.ChangedComputablePropEntityData",
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
                                                             name = "text",
                                                             valueType = primitiveTypeStringNotNullable,
                                                             withDefault = false),
                                         OwnPropertyMetadata(isComputable = true,
                                                             isKey = false,
                                                             isOpen = false,
                                                             name = "symbolicId",
                                                             valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                 typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                   fqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ChangedComputablePropEntityId",
                                                                                                                   properties = listOf(
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
                                                                                                                       name = "texts",
                                                                                                                       valueType = ValueTypeMetadata.ParameterizedType(
                                                                                                                         generics = listOf(
                                                                                                                           primitiveTypeStringNotNullable),
                                                                                                                         primitive = primitiveTypeListNotNullable),
                                                                                                                       withDefault = false)),
                                                                                                                   supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId"))),
                                                             withDefault = false)),
                     extProperties = listOf(),
                     isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata =
      EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ChangedComputablePropsOrderEntity",
                     entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.impl.ChangedComputablePropsOrderEntityData",
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
                                                             name = "someKey",
                                                             valueType = primitiveTypeIntNotNullable,
                                                             withDefault = false),
                                         OwnPropertyMetadata(isComputable = true,
                                                             isKey = false,
                                                             isOpen = false,
                                                             name = "symbolicId",
                                                             valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                 typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                   fqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ChangedComputablePropsOrderEntityId",
                                                                                                                   properties = listOf(
                                                                                                                     OwnPropertyMetadata(
                                                                                                                       isComputable = false,
                                                                                                                       isKey = false,
                                                                                                                       isOpen = false,
                                                                                                                       name = "names",
                                                                                                                       valueType = ValueTypeMetadata.ParameterizedType(
                                                                                                                         generics = listOf(
                                                                                                                           primitiveTypeStringNotNullable),
                                                                                                                         primitive = primitiveTypeListNotNullable),
                                                                                                                       withDefault = false),
                                                                                                                     OwnPropertyMetadata(
                                                                                                                       isComputable = false,
                                                                                                                       isKey = false,
                                                                                                                       isOpen = false,
                                                                                                                       name = "presentableName",
                                                                                                                       valueType = primitiveTypeStringNotNullable,
                                                                                                                       withDefault = false)),
                                                                                                                   supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId"))),
                                                             withDefault = false),
                                         OwnPropertyMetadata(isComputable = false,
                                                             isKey = false,
                                                             isOpen = false,
                                                             name = "names",
                                                             valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                               primitiveTypeStringNotNullable), primitive = primitiveTypeListNotNullable),
                                                             withDefault = false),
                                         OwnPropertyMetadata(isComputable = false,
                                                             isKey = false,
                                                             isOpen = false,
                                                             name = "value",
                                                             valueType = primitiveTypeIntNotNullable,
                                                             withDefault = false)),
                     extProperties = listOf(),
                     isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata =
      EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ChangedEnumNameEntity",
                     entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.impl.ChangedEnumNameEntityData",
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
                                                             name = "someEnum",
                                                             valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                 typeMetadata = FinalClassMetadata.EnumClassMetadata(
                                                                                                                   fqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ChangedEnumNameEnum",
                                                                                                                   properties = listOf(),
                                                                                                                   supertypes = listOf("java.io.Serializable",
                                                                                                                                       "kotlin.Comparable",
                                                                                                                                       "kotlin.Enum"),
                                                                                                                   values = listOf("A_ENTRY",
                                                                                                                                   "B_ENTRY",
                                                                                                                                   "CB_ENTRY"))),
                                                             withDefault = false)),
                     extProperties = listOf(),
                     isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata =
      EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ChangedPropsOrderEntity",
                     entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.impl.ChangedPropsOrderEntityData",
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
                                                             name = "version",
                                                             valueType = primitiveTypeIntNotNullable,
                                                             withDefault = false),
                                         OwnPropertyMetadata(isComputable = false,
                                                             isKey = false,
                                                             isOpen = false,
                                                             name = "string",
                                                             valueType = primitiveTypeStringNotNullable,
                                                             withDefault = false),
                                         OwnPropertyMetadata(isComputable = false,
                                                             isKey = false,
                                                             isOpen = false,
                                                             name = "data",
                                                             valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                 typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                   fqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ChangedPropsOrderDataClass",
                                                                                                                   properties = listOf(
                                                                                                                     OwnPropertyMetadata(
                                                                                                                       isComputable = false,
                                                                                                                       isKey = false,
                                                                                                                       isOpen = false,
                                                                                                                       name = "text",
                                                                                                                       valueType = primitiveTypeStringNotNullable,
                                                                                                                       withDefault = false),
                                                                                                                     OwnPropertyMetadata(
                                                                                                                       isComputable = false,
                                                                                                                       isKey = false,
                                                                                                                       isOpen = false,
                                                                                                                       name = "value",
                                                                                                                       valueType = primitiveTypeIntNotNullable,
                                                                                                                       withDefault = false)),
                                                                                                                   supertypes = listOf())),
                                                             withDefault = false),
                                         OwnPropertyMetadata(isComputable = false,
                                                             isKey = false,
                                                             isOpen = false,
                                                             name = "list",
                                                             valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                               ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                                 primitiveTypeIntNotNullable), primitive = primitiveTypeSetNotNullable)),
                                                                                                             primitive = primitiveTypeListNotNullable),
                                                             withDefault = false)),
                     extProperties = listOf(),
                     isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata =
      EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ChangedValueTypeEntity",
                     entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.impl.ChangedValueTypeEntityData",
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
                                                             name = "type",
                                                             valueType = primitiveTypeStringNotNullable,
                                                             withDefault = false),
                                         OwnPropertyMetadata(isComputable = false,
                                                             isKey = false,
                                                             isOpen = false,
                                                             name = "someKey",
                                                             valueType = primitiveTypeStringNotNullable,
                                                             withDefault = false),
                                         OwnPropertyMetadata(isComputable = false,
                                                             isKey = false,
                                                             isOpen = false,
                                                             name = "text",
                                                             valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                               primitiveTypeStringNotNullable), primitive = primitiveTypeListNotNullable),
                                                             withDefault = false)),
                     extProperties = listOf(),
                     isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata =
      EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ComputablePropEntity",
                     entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.impl.ComputablePropEntityData",
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
                                                             name = "list",
                                                             valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                               ValueTypeMetadata.ParameterizedType(generics = listOf(ValueTypeMetadata.ParameterizedType(
                                                                 generics = listOf(primitiveTypeIntNullable),
                                                                 primitive = primitiveTypeListNotNullable), primitiveTypeStringNotNullable),
                                                                                                   primitive = primitiveTypeMapNotNullable)),
                                                                                                             primitive = primitiveTypeListNotNullable),
                                                             withDefault = false),
                                         OwnPropertyMetadata(isComputable = false,
                                                             isKey = false,
                                                             isOpen = false,
                                                             name = "value",
                                                             valueType = primitiveTypeIntNotNullable,
                                                             withDefault = false),
                                         OwnPropertyMetadata(isComputable = false,
                                                             isKey = false,
                                                             isOpen = false,
                                                             name = "computableText",
                                                             valueType = primitiveTypeStringNotNullable,
                                                             withDefault = false)),
                     extProperties = listOf(),
                     isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.DefaultPropEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.impl.DefaultPropEntityData",
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
                                                                          name = "someString",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "someList",
                                                                          valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                                            primitiveTypeIntNotNullable),
                                                                                                                          primitive = primitiveTypeListNotNullable),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "constInt",
                                                                          valueType = primitiveTypeIntNotNullable,
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.EnumPropsEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.impl.EnumPropsEntityData",
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
                                                                          name = "someEnum",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.EnumClassMetadata(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.EnumPropsEnum",
                                                                                                                                properties = listOf(
                                                                                                                                  OwnPropertyMetadata(
                                                                                                                                    isComputable = false,
                                                                                                                                    isKey = false,
                                                                                                                                    isOpen = false,
                                                                                                                                    name = "value",
                                                                                                                                    valueType = primitiveTypeIntNotNullable,
                                                                                                                                    withDefault = false)),
                                                                                                                                supertypes = listOf(
                                                                                                                                  "java.io.Serializable",
                                                                                                                                  "kotlin.Comparable",
                                                                                                                                  "kotlin.Enum"),
                                                                                                                                values = listOf(
                                                                                                                                  "FIRST",
                                                                                                                                  "SECOND",
                                                                                                                                  "THIRD"))),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.KeyPropEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.impl.KeyPropEntityData",
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
                                                                          name = "someInt",
                                                                          valueType = primitiveTypeIntNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "text",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "url",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata =
      EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.NotNullToNullEntity",
                     entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.impl.NotNullToNullEntityData",
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
                                                             name = "nullInt",
                                                             valueType = primitiveTypeIntNullable,
                                                             withDefault = false),
                                         OwnPropertyMetadata(isComputable = false,
                                                             isKey = false,
                                                             isOpen = false,
                                                             name = "notNullString",
                                                             valueType = primitiveTypeStringNullable,
                                                             withDefault = false),
                                         OwnPropertyMetadata(isComputable = false,
                                                             isKey = false,
                                                             isOpen = false,
                                                             name = "notNullList",
                                                             valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                               primitiveTypeIntNotNullable), primitive = primitiveTypeListNotNullable),
                                                             withDefault = false)),
                     extProperties = listOf(),
                     isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata =
      EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.NullToNotNullEntity",
                     entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.impl.NullToNotNullEntityData",
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
                                                             name = "nullString",
                                                             valueType = primitiveTypeStringNotNullable,
                                                             withDefault = false),
                                         OwnPropertyMetadata(isComputable = false,
                                                             isKey = false,
                                                             isOpen = false,
                                                             name = "notNullBoolean",
                                                             valueType = primitiveTypeBooleanNotNullable,
                                                             withDefault = false),
                                         OwnPropertyMetadata(isComputable = false,
                                                             isKey = false,
                                                             isOpen = false,
                                                             name = "notNullInt",
                                                             valueType = primitiveTypeIntNotNullable,
                                                             withDefault = false)),
                     extProperties = listOf(),
                     isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata =
      EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.OneToManyRefEntity",
                     entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.impl.OneToManyRefEntityData",
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
                                                             valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                 typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                   fqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.OneToManyRefDataClass",
                                                                                                                   properties = listOf(
                                                                                                                     OwnPropertyMetadata(
                                                                                                                       isComputable = false,
                                                                                                                       isKey = false,
                                                                                                                       isOpen = false,
                                                                                                                       name = "list",
                                                                                                                       valueType = ValueTypeMetadata.ParameterizedType(
                                                                                                                         generics = listOf(
                                                                                                                           ValueTypeMetadata.ParameterizedType(
                                                                                                                             generics = listOf(
                                                                                                                               primitiveTypeStringNotNullable),
                                                                                                                             primitive = primitiveTypeSetNotNullable)),
                                                                                                                         primitive = primitiveTypeListNotNullable),
                                                                                                                       withDefault = false),
                                                                                                                     OwnPropertyMetadata(
                                                                                                                       isComputable = false,
                                                                                                                       isKey = false,
                                                                                                                       isOpen = false,
                                                                                                                       name = "value",
                                                                                                                       valueType = primitiveTypeIntNotNullable,
                                                                                                                       withDefault = false)),
                                                                                                                   supertypes = listOf())),
                                                             withDefault = false),
                                         OwnPropertyMetadata(isComputable = false,
                                                             isKey = false,
                                                             isOpen = false,
                                                             name = "anotherEntity",
                                                             valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                                           entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.AnotherOneToManyRefEntity",
                                                                                                           isChild = true,
                                                                                                           isNullable = true),
                                                             withDefault = false)),
                     extProperties = listOf(),
                     isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.OneToOneRefEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.impl.OneToOneRefEntityData",
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
                                                                          name = "version",
                                                                          valueType = primitiveTypeIntNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "text",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "anotherEntity",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.AnotherOneToOneRefEntity",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = false),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata =
      EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SimpleObjectsEntity",
                     entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.impl.SimpleObjectsEntityData",
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
                                                             valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                 typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(
                                                                                                                   fqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SimpleObjectsSealedClass",
                                                                                                                   subclasses = listOf(
                                                                                                                     FinalClassMetadata.ObjectMetadata(
                                                                                                                       fqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SimpleObjectsSealedClass\$SecondSimpleObjectsSealedClassObject",
                                                                                                                       properties = listOf(
                                                                                                                         OwnPropertyMetadata(
                                                                                                                           isComputable = false,
                                                                                                                           isKey = false,
                                                                                                                           isOpen = false,
                                                                                                                           name = "data",
                                                                                                                           valueType = primitiveTypeStringNotNullable,
                                                                                                                           withDefault = false),
                                                                                                                         OwnPropertyMetadata(
                                                                                                                           isComputable = false,
                                                                                                                           isKey = false,
                                                                                                                           isOpen = false,
                                                                                                                           name = "id",
                                                                                                                           valueType = primitiveTypeIntNotNullable,
                                                                                                                           withDefault = false),
                                                                                                                         OwnPropertyMetadata(
                                                                                                                           isComputable = false,
                                                                                                                           isKey = false,
                                                                                                                           isOpen = false,
                                                                                                                           name = "list",
                                                                                                                           valueType = ValueTypeMetadata.ParameterizedType(
                                                                                                                             generics = listOf(
                                                                                                                               primitiveTypeStringNotNullable),
                                                                                                                             primitive = primitiveTypeListNotNullable),
                                                                                                                           withDefault = false),
                                                                                                                         OwnPropertyMetadata(
                                                                                                                           isComputable = false,
                                                                                                                           isKey = false,
                                                                                                                           isOpen = false,
                                                                                                                           name = "listSize",
                                                                                                                           valueType = primitiveTypeIntNotNullable,
                                                                                                                           withDefault = false)),
                                                                                                                       supertypes = listOf("com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SimpleObjectsSealedClass")),
                                                                                                                     FinalClassMetadata.ObjectMetadata(
                                                                                                                       fqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SimpleObjectsSealedClass\$FirstSimpleObjectsSealedClassObject",
                                                                                                                       properties = listOf(
                                                                                                                         OwnPropertyMetadata(
                                                                                                                           isComputable = false,
                                                                                                                           isKey = false,
                                                                                                                           isOpen = false,
                                                                                                                           name = "data",
                                                                                                                           valueType = primitiveTypeStringNotNullable,
                                                                                                                           withDefault = false),
                                                                                                                         OwnPropertyMetadata(
                                                                                                                           isComputable = false,
                                                                                                                           isKey = false,
                                                                                                                           isOpen = false,
                                                                                                                           name = "id",
                                                                                                                           valueType = primitiveTypeIntNotNullable,
                                                                                                                           withDefault = false),
                                                                                                                         OwnPropertyMetadata(
                                                                                                                           isComputable = false,
                                                                                                                           isKey = false,
                                                                                                                           isOpen = false,
                                                                                                                           name = "value",
                                                                                                                           valueType = primitiveTypeIntNotNullable,
                                                                                                                           withDefault = false)),
                                                                                                                       supertypes = listOf("com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SimpleObjectsSealedClass"))),
                                                                                                                   supertypes = listOf())),
                                                             withDefault = false)),
                     extProperties = listOf(),
                     isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SimplePropsEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.impl.SimplePropsEntityData",
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
                                                                          name = "text",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "list",
                                                                          valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                                            primitiveTypeIntNotNullable),
                                                                                                                          primitive = primitiveTypeListNotNullable),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "set",
                                                                          valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                                            ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                                              primitiveTypeStringNotNullable),
                                                                                                                primitive = primitiveTypeListNotNullable)),
                                                                                                                          primitive = primitiveTypeSetNotNullable),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "map",
                                                                          valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                                            ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                                              primitiveTypeStringNotNullable),
                                                                                                                primitive = primitiveTypeSetNotNullable),
                                                                            ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                                              primitiveTypeStringNotNullable),
                                                                                                                primitive = primitiveTypeListNotNullable)),
                                                                                                                          primitive = primitiveTypeMapNotNullable),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "bool",
                                                                          valueType = primitiveTypeBooleanNotNullable,
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata =
      EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SimpleSealedClassEntity",
                     entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.impl.SimpleSealedClassEntityData",
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
                                                             name = "text",
                                                             valueType = primitiveTypeStringNotNullable,
                                                             withDefault = false),
                                         OwnPropertyMetadata(isComputable = false,
                                                             isKey = false,
                                                             isOpen = false,
                                                             name = "someData",
                                                             valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                 typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(
                                                                                                                   fqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SimpleSealedClass",
                                                                                                                   subclasses = listOf(
                                                                                                                     FinalClassMetadata.ClassMetadata(
                                                                                                                       fqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SimpleSealedClass\$SecondKeyPropDataClass",
                                                                                                                       properties = listOf(
                                                                                                                         OwnPropertyMetadata(
                                                                                                                           isComputable = false,
                                                                                                                           isKey = false,
                                                                                                                           isOpen = false,
                                                                                                                           name = "list",
                                                                                                                           valueType = ValueTypeMetadata.ParameterizedType(
                                                                                                                             generics = listOf(
                                                                                                                               primitiveTypeStringNotNullable),
                                                                                                                             primitive = primitiveTypeListNotNullable),
                                                                                                                           withDefault = false),
                                                                                                                         OwnPropertyMetadata(
                                                                                                                           isComputable = false,
                                                                                                                           isKey = false,
                                                                                                                           isOpen = false,
                                                                                                                           name = "type",
                                                                                                                           valueType = primitiveTypeStringNotNullable,
                                                                                                                           withDefault = false),
                                                                                                                         OwnPropertyMetadata(
                                                                                                                           isComputable = false,
                                                                                                                           isKey = false,
                                                                                                                           isOpen = false,
                                                                                                                           name = "value",
                                                                                                                           valueType = primitiveTypeIntNotNullable,
                                                                                                                           withDefault = false)),
                                                                                                                       supertypes = listOf("com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SimpleSealedClass")),
                                                                                                                     FinalClassMetadata.ClassMetadata(
                                                                                                                       fqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SimpleSealedClass\$FirstKeyPropDataClass",
                                                                                                                       properties = listOf(
                                                                                                                         OwnPropertyMetadata(
                                                                                                                           isComputable = false,
                                                                                                                           isKey = false,
                                                                                                                           isOpen = false,
                                                                                                                           name = "text",
                                                                                                                           valueType = primitiveTypeStringNotNullable,
                                                                                                                           withDefault = false),
                                                                                                                         OwnPropertyMetadata(
                                                                                                                           isComputable = false,
                                                                                                                           isKey = false,
                                                                                                                           isOpen = false,
                                                                                                                           name = "type",
                                                                                                                           valueType = primitiveTypeStringNotNullable,
                                                                                                                           withDefault = false)),
                                                                                                                       supertypes = listOf("com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SimpleSealedClass"))),
                                                                                                                   supertypes = listOf())),
                                                             withDefault = false)),
                     extProperties = listOf(),
                     isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SubsetEnumEntity",
                                  entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.impl.SubsetEnumEntityData",
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
                                                                          name = "someEnum",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.EnumClassMetadata(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SubsetEnumEnum",
                                                                                                                                properties = listOf(
                                                                                                                                  OwnPropertyMetadata(
                                                                                                                                    isComputable = false,
                                                                                                                                    isKey = false,
                                                                                                                                    isOpen = false,
                                                                                                                                    name = "type",
                                                                                                                                    valueType = primitiveTypeStringNotNullable,
                                                                                                                                    withDefault = false)),
                                                                                                                                supertypes = listOf(
                                                                                                                                  "java.io.Serializable",
                                                                                                                                  "kotlin.Comparable",
                                                                                                                                  "kotlin.Enum"),
                                                                                                                                values = listOf(
                                                                                                                                  "A_ENUM",
                                                                                                                                  "B_ENUM",
                                                                                                                                  "C_ENUM",
                                                                                                                                  "D_ENUM",
                                                                                                                                  "E_ENUM"))),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata =
      EntityMetadata(fqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SubsetSealedClassEntity",
                     entityDataFqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.impl.SubsetSealedClassEntityData",
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
                                                             valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                 typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(
                                                                                                                   fqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SubsetSealedClass",
                                                                                                                   subclasses = listOf(
                                                                                                                     FinalClassMetadata.ClassMetadata(
                                                                                                                       fqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SubsetSealedClass\$FirstSubsetSealedClassDataClass",
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
                                                                                                                       supertypes = listOf("com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SubsetSealedClass")),
                                                                                                                     FinalClassMetadata.ClassMetadata(
                                                                                                                       fqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SubsetSealedClass\$SecondSubsetSealedClassDataClass",
                                                                                                                       properties = listOf(
                                                                                                                         OwnPropertyMetadata(
                                                                                                                           isComputable = false,
                                                                                                                           isKey = false,
                                                                                                                           isOpen = false,
                                                                                                                           name = "list",
                                                                                                                           valueType = ValueTypeMetadata.ParameterizedType(
                                                                                                                             generics = listOf(
                                                                                                                               primitiveTypeIntNotNullable),
                                                                                                                             primitive = primitiveTypeListNotNullable),
                                                                                                                           withDefault = false),
                                                                                                                         OwnPropertyMetadata(
                                                                                                                           isComputable = false,
                                                                                                                           isKey = false,
                                                                                                                           isOpen = false,
                                                                                                                           name = "name",
                                                                                                                           valueType = primitiveTypeStringNotNullable,
                                                                                                                           withDefault = false)),
                                                                                                                       supertypes = listOf("com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SubsetSealedClass")),
                                                                                                                     FinalClassMetadata.ObjectMetadata(
                                                                                                                       fqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SubsetSealedClass\$FirstSubsetSealedClassObject",
                                                                                                                       properties = listOf(
                                                                                                                         OwnPropertyMetadata(
                                                                                                                           isComputable = false,
                                                                                                                           isKey = false,
                                                                                                                           isOpen = false,
                                                                                                                           name = "name",
                                                                                                                           valueType = primitiveTypeStringNotNullable,
                                                                                                                           withDefault = false)),
                                                                                                                       supertypes = listOf("com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SubsetSealedClass")),
                                                                                                                     FinalClassMetadata.ObjectMetadata(
                                                                                                                       fqName = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SubsetSealedClass\$SecondSubsetSealedClassObject",
                                                                                                                       properties = listOf(
                                                                                                                         OwnPropertyMetadata(
                                                                                                                           isComputable = false,
                                                                                                                           isKey = false,
                                                                                                                           isOpen = false,
                                                                                                                           name = "name",
                                                                                                                           valueType = primitiveTypeStringNotNullable,
                                                                                                                           withDefault = false)),
                                                                                                                       supertypes = listOf("com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SubsetSealedClass"))),
                                                                                                                   supertypes = listOf())),
                                                             withDefault = false)),
                     extProperties = listOf(),
                     isAbstract = false)

    addMetadata(typeMetadata)
  }

  override fun initializeMetadataHash() {
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.AbstractChildEntity",
                    metadataHash = -1515875629)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.AbstractChildWithLinkToParentEntity",
                    metadataHash = 699335)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.AbstractParentEntity",
                    metadataHash = -1612666392)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.AssertConsistencyEntity",
                    metadataHash = 415809930)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.AttachedEntity", metadataHash = 1471156741)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.AttachedEntityList", metadataHash = 672718885)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.AttachedEntityParentList",
                    metadataHash = -915263826)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.AttachedEntityToNullableParent",
                    metadataHash = -698163598)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.AttachedEntityToParent",
                    metadataHash = 1059318708)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.BaseEntity", metadataHash = -336060765)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.BooleanEntity", metadataHash = -1088987449)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.ChainedEntity", metadataHash = 1767816450)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.ChainedParentEntity",
                    metadataHash = 1242015480)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.ChildAbstractBaseEntity",
                    metadataHash = -560510087)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.ChildEntity", metadataHash = -772115112)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.ChildEntityWithSymbolicId",
                    metadataHash = 475507196)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.ChildFirstEntity", metadataHash = 136354378)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.ChildMultipleEntity",
                    metadataHash = 2117547633)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.ChildSampleEntity", metadataHash = -517942108)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.ChildSecondEntity", metadataHash = -2019764788)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.ChildSingleAbstractBaseEntity",
                    metadataHash = -91237058)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.ChildSingleFirstEntity",
                    metadataHash = -1716989179)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.ChildSingleSecondEntity",
                    metadataHash = -1604964023)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.ChildSourceEntity", metadataHash = 1778924990)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.ChildSubEntity", metadataHash = -448606750)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.ChildSubSubEntity", metadataHash = 227794706)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.ChildWithExtensionParent",
                    metadataHash = -931793730)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.ChildWithId", metadataHash = 1784899078)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.ChildWithNulls", metadataHash = 1373563594)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.ChildWithNullsMultiple",
                    metadataHash = 1291039274)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.ChildWithNullsOppositeMultiple",
                    metadataHash = -58117742)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.ChildWpidSampleEntity",
                    metadataHash = -258889065)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.CollectionFieldEntity",
                    metadataHash = -937432852)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.ComposedIdSoftRefEntity",
                    metadataHash = -132517145)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.ComposedLinkEntity", metadataHash = -660383078)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.CompositeAbstractEntity",
                    metadataHash = -287072494)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.CompositeBaseEntity",
                    metadataHash = -1491348073)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.CompositeChildAbstractEntity",
                    metadataHash = 1783740694)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.ContentRootTestEntity",
                    metadataHash = 124427640)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.DefaultValueEntity", metadataHash = -385405311)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.EntityWithSoftLinks",
                    metadataHash = -385677619)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.FacetTestEntity", metadataHash = -41304966)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.FinalFieldsEntity", metadataHash = 1742584926)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.FirstEntityWithPId", metadataHash = 14699824)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.GrandParentWithId", metadataHash = 437281960)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.HeadAbstractionEntity",
                    metadataHash = -2011738162)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.IntEntity", metadataHash = 1804731520)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.KeyChild", metadataHash = 1499868609)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.KeyParent", metadataHash = 1615759425)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.LeftEntity", metadataHash = -1589966368)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.LinkedListEntity", metadataHash = 1888101100)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.ListEntity", metadataHash = -752357132)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.ListVFUEntity", metadataHash = -775928952)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.MainEntity", metadataHash = 223023004)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.MainEntityList", metadataHash = 649455768)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.MainEntityParentList",
                    metadataHash = -260771205)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.MainEntityToParent",
                    metadataHash = -1758157325)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.MiddleEntity", metadataHash = -1242925205)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.ModuleTestEntity", metadataHash = -1013119376)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.NamedChildEntity", metadataHash = -1421345485)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.NamedEntity", metadataHash = 1380470139)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.NullableVFUEntity", metadataHash = -759625657)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.OneEntityWithSymbolicId",
                    metadataHash = -461494665)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.OoChildAlsoWithPidEntity",
                    metadataHash = -481402623)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.OoChildEntity", metadataHash = -1435175027)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.OoChildForParentWithPidEntity",
                    metadataHash = 815392598)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.OoChildWithNullableParentEntity",
                    metadataHash = 1181940982)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.OoChildWithPidEntity",
                    metadataHash = 776388711)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.OoParentEntity", metadataHash = 988109523)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.OoParentWithPidEntity",
                    metadataHash = 1235175938)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.OoParentWithoutPidEntity",
                    metadataHash = -164182327)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.OptionalIntEntity", metadataHash = -1130678681)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.OptionalOneToOneChildEntity",
                    metadataHash = -2086249717)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.OptionalOneToOneParentEntity",
                    metadataHash = 1884494395)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.OptionalStringEntity",
                    metadataHash = -672020605)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.ParentAbEntity", metadataHash = -1424467887)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.ParentChainEntity", metadataHash = -1605345680)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.ParentEntity", metadataHash = -1642119549)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.ParentEntityWithSymbolicId",
                    metadataHash = -916004223)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.ParentMultipleEntity",
                    metadataHash = 1383456438)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.ParentSingleAbEntity",
                    metadataHash = -1218536084)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.ParentSubEntity", metadataHash = -1431073415)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.ParentWithExtensionEntity",
                    metadataHash = -1031895366)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.ParentWithId", metadataHash = 123434002)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.ParentWithLinkToAbstractChild",
                    metadataHash = 606433357)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.ParentWithNulls", metadataHash = -49459087)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.ParentWithNullsMultiple",
                    metadataHash = 918511794)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.ParentWithNullsOppositeMultiple",
                    metadataHash = -1800940400)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.PlaceholderEntity", metadataHash = 358103773)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.ProjectModelTestEntity",
                    metadataHash = -1006149100)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.RightEntity", metadataHash = 859539838)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.SampleEntity", metadataHash = 1968519041)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.SampleEntity2", metadataHash = -559973930)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.SampleWithSymbolicIdEntity",
                    metadataHash = 498065903)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.SecondEntityWithPId", metadataHash = 778307180)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.SecondSampleEntity", metadataHash = 438300736)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.SelfLinkedEntity", metadataHash = 53090952)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.SetVFUEntity", metadataHash = 1068409070)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.SimpleAbstractEntity",
                    metadataHash = 1451842232)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.SimpleChildAbstractEntity",
                    metadataHash = 996781535)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.SoftLinkReferencedChild",
                    metadataHash = -401670753)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.SourceEntity", metadataHash = 676902393)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.SourceRootTestEntity",
                    metadataHash = -606128106)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.SourceRootTestOrderEntity",
                    metadataHash = 1522689541)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.SpecificChildEntity", metadataHash = 130347079)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.SpecificChildWithLinkToParentEntity",
                    metadataHash = -168058859)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.SpecificParent", metadataHash = -1864129705)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.StringEntity", metadataHash = -426624284)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.SymbolicIdEntity", metadataHash = -766290202)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.TreeEntity", metadataHash = -968982890)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.TreeMultiparentLeafEntity",
                    metadataHash = 820942010)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.TreeMultiparentRootEntity",
                    metadataHash = 858112633)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.UnknownFieldEntity",
                    metadataHash = -1700871488)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.VFUEntity", metadataHash = -1882565136)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.VFUEntity2", metadataHash = -1507119292)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.VFUWithTwoPropertiesEntity",
                    metadataHash = 422599282)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.WithListSoftLinksEntity",
                    metadataHash = 2115673411)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.WithSealedEntity", metadataHash = -1912529824)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.WithSoftLinkEntity",
                    metadataHash = -1151955000)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.XChildChildEntity", metadataHash = -885558186)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.XChildEntity", metadataHash = -389364848)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.XChildWithOptionalParentEntity",
                    metadataHash = -1324245436)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.XParentEntity", metadataHash = -2146280434)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.AnotherOneToManyRefEntity",
                    metadataHash = 1245340867)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.AnotherOneToOneRefEntity",
                    metadataHash = -1375003387)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ChangedComputablePropEntity",
                    metadataHash = 815162342)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ChangedComputablePropsOrderEntity",
                    metadataHash = -1842443614)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ChangedEnumNameEntity",
                    metadataHash = 1043581846)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ChangedPropsOrderEntity",
                    metadataHash = 252648040)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ChangedValueTypeEntity",
                    metadataHash = 2035231852)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ComputablePropEntity",
                    metadataHash = 1476815671)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.DefaultPropEntity",
                    metadataHash = 1243569014)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.EnumPropsEntity",
                    metadataHash = 2095175456)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.KeyPropEntity",
                    metadataHash = -1424456810)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.NotNullToNullEntity",
                    metadataHash = 402114472)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.NullToNotNullEntity",
                    metadataHash = -1810358941)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.OneToManyRefEntity",
                    metadataHash = -1930784175)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.OneToOneRefEntity",
                    metadataHash = -969709640)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SimpleObjectsEntity",
                    metadataHash = -2022415466)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SimplePropsEntity",
                    metadataHash = 119295682)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SimpleSealedClassEntity",
                    metadataHash = -237999681)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SubsetEnumEntity",
                    metadataHash = 842269939)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SubsetSealedClassEntity",
                    metadataHash = -1593513448)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.AnotherOneToManyRefEntity",
                    metadataHash = 586498122)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.AnotherOneToOneRefEntity",
                    metadataHash = 1325560709)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ChangedComputablePropEntity",
                    metadataHash = 815162342)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ChangedComputablePropsOrderEntity",
                    metadataHash = -112489681)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ChangedEnumNameEntity",
                    metadataHash = -1854549984)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ChangedPropsOrderEntity",
                    metadataHash = -526415617)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ChangedValueTypeEntity",
                    metadataHash = -1669946448)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ComputablePropEntity",
                    metadataHash = 1222863115)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.DefaultPropEntity",
                    metadataHash = 1422735673)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.EnumPropsEntity",
                    metadataHash = 2095175456)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.KeyPropEntity",
                    metadataHash = -246764551)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.NotNullToNullEntity",
                    metadataHash = -503113989)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.NullToNotNullEntity",
                    metadataHash = -1876047004)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.OneToManyRefEntity",
                    metadataHash = -1738921955)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.OneToOneRefEntity",
                    metadataHash = 1949857225)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SimpleObjectsEntity",
                    metadataHash = 1560477313)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SimplePropsEntity",
                    metadataHash = 722267412)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SimpleSealedClassEntity",
                    metadataHash = -1131962339)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SubsetEnumEntity",
                    metadataHash = 1509299022)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SubsetSealedClassEntity",
                    metadataHash = -303049617)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.ChildNameIdWithParentId",
                    metadataHash = 1035855468)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.ParentNameId", metadataHash = -1341386657)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.ChildWithId\$ChildId",
                    metadataHash = -2096208751)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.NameId", metadataHash = -435151531)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.ComposedId", metadataHash = 1096963903)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.OneSymbolicId", metadataHash = 1365168100)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.Container", metadataHash = 2121842459)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.TooDeepContainer", metadataHash = 733597688)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.DeepContainer", metadataHash = 150790775)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.SealedContainer", metadataHash = -1779356415)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.SealedContainer\$BigContainer",
                    metadataHash = 1735400316)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.SealedContainer\$ContainerContainer",
                    metadataHash = -540830456)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.SealedContainer\$EmptyContainer",
                    metadataHash = -445307259)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.SealedContainer\$SmallContainer",
                    metadataHash = -1319910747)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.DeepSealedOne", metadataHash = -1513011887)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.DeepSealedOne\$DeepSealedTwo",
                    metadataHash = -2090020574)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.DeepSealedOne\$DeepSealedTwo\$DeepSealedThree",
                    metadataHash = -80392179)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.DeepSealedOne\$DeepSealedTwo\$DeepSealedThree\$DeepSealedFour",
                    metadataHash = 984189067)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.FacetTestEntitySymbolicId",
                    metadataHash = -877189206)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.AnotherDataClass", metadataHash = 1727810557)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.FirstPId", metadataHash = -103607010)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.GrandParentWithId\$GrandParentId",
                    metadataHash = -168491119)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.HeadAbstractionSymbolicId",
                    metadataHash = 2069054680)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.LinkedListEntityId", metadataHash = -158800092)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.ModuleTestEntitySymbolicId",
                    metadataHash = 1128583613)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.OoChildEntityId", metadataHash = 1896179787)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.OoParentEntityId", metadataHash = -1856598089)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.ParentWithId\$ParentId",
                    metadataHash = -1744643753)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.Descriptor", metadataHash = -356784219)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.DescriptorInstance", metadataHash = -716106614)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.SampleSymbolicId", metadataHash = -96229719)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.SecondPId", metadataHash = -440847684)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.TreeMultiparentSymbolicId",
                    metadataHash = -1755156316)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.AnotherNameId", metadataHash = 1076243546)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.MySealedClass", metadataHash = -1890212324)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.MySealedClassOne", metadataHash = -779940407)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.MySealedClassTwo", metadataHash = -1259351121)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.MySealedInterface", metadataHash = -1649742145)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.MySealedInterfaceOne",
                    metadataHash = 528982407)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.MySealedInterfaceTwo",
                    metadataHash = 1370686573)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.DataClassX", metadataHash = -1389837364)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.OneToManyRefDataClass",
                    metadataHash = -1148644517)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ChangedComputablePropEntityId",
                    metadataHash = 2005024702)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ChangedComputablePropsOrderEntityId",
                    metadataHash = 157707118)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ChangedEnumNameEnum",
                    metadataHash = 1450112092)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ChangedPropsOrderDataClass",
                    metadataHash = 688668393)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.EnumPropsEnum",
                    metadataHash = 1833335773)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SimpleObjectsSealedClass",
                    metadataHash = 461547503)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SimpleObjectsSealedClass\$FirstSimpleObjectsSealedClassObject",
                    metadataHash = -1889523869)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SimpleObjectsSealedClass\$SecondSimpleObjectsSealedClassObject",
                    metadataHash = -354288000)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SimpleSealedClass",
                    metadataHash = -159047680)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SimpleSealedClass\$FirstKeyPropDataClass",
                    metadataHash = 1781544109)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SimpleSealedClass\$SecondKeyPropDataClass",
                    metadataHash = 1541918065)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SubsetEnumEnum",
                    metadataHash = -1129253965)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SubsetSealedClass",
                    metadataHash = -158426435)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SubsetSealedClass\$FirstSubsetSealedClassDataClass",
                    metadataHash = 215409594)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SubsetSealedClass\$FirstSubsetSealedClassObject",
                    metadataHash = -1642784700)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SubsetSealedClass\$SecondSubsetSealedClassDataClass",
                    metadataHash = 1024597119)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.OneToManyRefDataClass",
                    metadataHash = -1243683388)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ChangedComputablePropEntityId",
                    metadataHash = 459283880)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ChangedComputablePropsOrderEntityId",
                    metadataHash = 853052759)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ChangedEnumNameEnum",
                    metadataHash = 1186141682)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ChangedPropsOrderDataClass",
                    metadataHash = 1170529618)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.EnumPropsEnum",
                    metadataHash = 1833335773)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SimpleObjectsSealedClass",
                    metadataHash = 1536694654)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SimpleObjectsSealedClass\$FirstSimpleObjectsSealedClassObject",
                    metadataHash = 1185517379)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SimpleObjectsSealedClass\$SecondSimpleObjectsSealedClassObject",
                    metadataHash = 1030341298)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SimpleSealedClass",
                    metadataHash = 802916040)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SimpleSealedClass\$FirstKeyPropDataClass",
                    metadataHash = -1387098291)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SimpleSealedClass\$SecondKeyPropDataClass",
                    metadataHash = -1507776974)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SubsetEnumEnum",
                    metadataHash = 1051569930)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SubsetSealedClass",
                    metadataHash = 1792636160)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SubsetSealedClass\$FirstSubsetSealedClassDataClass",
                    metadataHash = 397260186)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SubsetSealedClass\$FirstSubsetSealedClassObject",
                    metadataHash = 1315739634)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SubsetSealedClass\$SecondSubsetSealedClassDataClass",
                    metadataHash = -471982035)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SubsetSealedClass\$SecondSubsetSealedClassObject",
                    metadataHash = 2030799620)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.EntitySource", metadataHash = 1957311550)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.AnotherSource", metadataHash = 1783283309)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.MyDummyParentSource",
                    metadataHash = 1772080837)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.MySource", metadataHash = -866848740)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.SampleEntitySource", metadataHash = 2075294772)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.testEntities.entities.VFUEntitySource", metadataHash = 1995493998)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.SymbolicEntityId", metadataHash = 1322656805)
  }
}
