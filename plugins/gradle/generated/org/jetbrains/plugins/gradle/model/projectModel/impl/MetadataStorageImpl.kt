// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model.projectModel.impl

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

    typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "org.jetbrains.plugins.gradle.model.projectModel.GradleBuildEntityId",
                                                    properties = listOf(
                                                      OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false,
                                                                          name = "codeCache", valueType = primitiveTypeIntNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false,
                                                                          name = "externalProjectId",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                            isNullable = false,
                                                                            typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                              fqName = "com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntityId",
                                                                              properties = listOf(
                                                                                OwnPropertyMetadata(isComputable = false, isKey = false,
                                                                                                    isOpen = false, name = "codeCache",
                                                                                                    valueType = primitiveTypeIntNotNullable,
                                                                                                    withDefault = false),
                                                                                OwnPropertyMetadata(isComputable = false, isKey = false,
                                                                                                    isOpen = false,
                                                                                                    name = "externalProjectPath",
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
                                                      OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false,
                                                                          name = "presentableName",
                                                                          valueType = primitiveTypeStringNotNullable, withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "url",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                            isNullable = false,
                                                                            typeMetadata = FinalClassMetadata.KnownClass(
                                                                              fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                                                                          withDefault = false)),
                                                    supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId"))

    addMetadata(typeMetadata)

    typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "org.jetbrains.plugins.gradle.model.projectModel.GradleProjectEntityId",
                                                    properties = listOf(
                                                      OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false,
                                                                          name = "buildId",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                            isNullable = false,
                                                                            typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                              fqName = "org.jetbrains.plugins.gradle.model.projectModel.GradleBuildEntityId",
                                                                              properties = listOf(
                                                                                OwnPropertyMetadata(isComputable = false, isKey = false,
                                                                                                    isOpen = false, name = "codeCache",
                                                                                                    valueType = primitiveTypeIntNotNullable,
                                                                                                    withDefault = false),
                                                                                OwnPropertyMetadata(isComputable = false, isKey = false,
                                                                                                    isOpen = false,
                                                                                                    name = "externalProjectId",
                                                                                                    valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                                      isNullable = false,
                                                                                                      typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                        fqName = "com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntityId",
                                                                                                        properties = listOf(
                                                                                                          OwnPropertyMetadata(
                                                                                                            isComputable = false,
                                                                                                            isKey = false, isOpen = false,
                                                                                                            name = "codeCache",
                                                                                                            valueType = primitiveTypeIntNotNullable,
                                                                                                            withDefault = false),
                                                                                                          OwnPropertyMetadata(
                                                                                                            isComputable = false,
                                                                                                            isKey = false, isOpen = false,
                                                                                                            name = "externalProjectPath",
                                                                                                            valueType = primitiveTypeStringNotNullable,
                                                                                                            withDefault = false),
                                                                                                          OwnPropertyMetadata(
                                                                                                            isComputable = false,
                                                                                                            isKey = false, isOpen = false,
                                                                                                            name = "presentableName",
                                                                                                            valueType = primitiveTypeStringNotNullable,
                                                                                                            withDefault = false)),
                                                                                                        supertypes = listOf(
                                                                                                          "com.intellij.platform.workspace.storage.SymbolicEntityId"))),
                                                                                                    withDefault = false),
                                                                                OwnPropertyMetadata(isComputable = false, isKey = false,
                                                                                                    isOpen = false,
                                                                                                    name = "presentableName",
                                                                                                    valueType = primitiveTypeStringNotNullable,
                                                                                                    withDefault = false),
                                                                                OwnPropertyMetadata(isComputable = false, isKey = false,
                                                                                                    isOpen = false, name = "url",
                                                                                                    valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                                      isNullable = false,
                                                                                                      typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                        fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                                                                                                    withDefault = false)),
                                                                              supertypes = listOf(
                                                                                "com.intellij.platform.workspace.storage.SymbolicEntityId"))),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false,
                                                                          name = "codeCache", valueType = primitiveTypeIntNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false,
                                                                          name = "presentableName",
                                                                          valueType = primitiveTypeStringNotNullable, withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "url",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                            isNullable = false,
                                                                            typeMetadata = FinalClassMetadata.KnownClass(
                                                                              fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                                                                          withDefault = false)),
                                                    supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId"))

    addMetadata(typeMetadata)

    typeMetadata = FinalClassMetadata.ClassMetadata(
      fqName = "org.jetbrains.plugins.gradle.service.syncAction.impl.bridge.GradleBridgeModuleDataService\$GradleBridgeModuleEntitySource",
      properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "virtualFileUrl",
                                              valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = true,
                                                                                                  typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                    fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                                              withDefault = false)),
      supertypes = listOf("com.intellij.platform.workspace.storage.EntitySource"))

    addMetadata(typeMetadata)

    typeMetadata = FinalClassMetadata.ClassMetadata(
      fqName = "org.jetbrains.plugins.gradle.service.syncAction.impl.contributors.GradleVersionCatalogSyncContributor\$GradleVersionCatalogEntitySource",
      properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "phase",
                                              valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                  typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(
                                                                                                    fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase",
                                                                                                    subclasses = listOf(
                                                                                                      FinalClassMetadata.ClassMetadata(
                                                                                                        fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleDynamicSyncPhase",
                                                                                                        properties = listOf(
                                                                                                          OwnPropertyMetadata(
                                                                                                            isComputable = false,
                                                                                                            isKey = false, isOpen = false,
                                                                                                            name = "modelFetchPhase",
                                                                                                            valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                                              isNullable = false,
                                                                                                              typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(
                                                                                                                fqName = "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase",
                                                                                                                subclasses = listOf(
                                                                                                                  FinalClassMetadata.ClassMetadata(
                                                                                                                    fqName = "com.intellij.gradle.toolingExtension.modelAction.GradleBuildFinishedModelFetchPhase",
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
                                                                                                                        name = "order",
                                                                                                                        valueType = primitiveTypeIntNotNullable,
                                                                                                                        withDefault = false)),
                                                                                                                    supertypes = listOf(
                                                                                                                      "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase",
                                                                                                                      "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase\$BuildFinished",
                                                                                                                      "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase.BuildFinished",
                                                                                                                      "java.io.Serializable",
                                                                                                                      "kotlin.Comparable")),
                                                                                                                  FinalClassMetadata.ClassMetadata(
                                                                                                                    fqName = "com.intellij.gradle.toolingExtension.modelAction.GradleProjectLoadedModelFetchPhase",
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
                                                                                                                        name = "order",
                                                                                                                        valueType = primitiveTypeIntNotNullable,
                                                                                                                        withDefault = false)),
                                                                                                                    supertypes = listOf(
                                                                                                                      "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase",
                                                                                                                      "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase\$ProjectLoaded",
                                                                                                                      "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase.ProjectLoaded",
                                                                                                                      "java.io.Serializable",
                                                                                                                      "kotlin.Comparable"))),
                                                                                                                supertypes = listOf(
                                                                                                                  "java.io.Serializable",
                                                                                                                  "java.lang.Comparable",
                                                                                                                  "kotlin.Comparable"))),
                                                                                                            withDefault = false),
                                                                                                          OwnPropertyMetadata(
                                                                                                            isComputable = false,
                                                                                                            isKey = false, isOpen = false,
                                                                                                            name = "name",
                                                                                                            valueType = primitiveTypeStringNotNullable,
                                                                                                            withDefault = false)),
                                                                                                        supertypes = listOf(
                                                                                                          "kotlin.Comparable",
                                                                                                          "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase",
                                                                                                          "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase\$Dynamic",
                                                                                                          "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase.Dynamic")),
                                                                                                      FinalClassMetadata.ClassMetadata(
                                                                                                        fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleStaticSyncPhase",
                                                                                                        properties = listOf(
                                                                                                          OwnPropertyMetadata(
                                                                                                            isComputable = false,
                                                                                                            isKey = false, isOpen = false,
                                                                                                            name = "name",
                                                                                                            valueType = primitiveTypeStringNotNullable,
                                                                                                            withDefault = false),
                                                                                                          OwnPropertyMetadata(
                                                                                                            isComputable = false,
                                                                                                            isKey = false, isOpen = false,
                                                                                                            name = "order",
                                                                                                            valueType = primitiveTypeIntNotNullable,
                                                                                                            withDefault = false)),
                                                                                                        supertypes = listOf(
                                                                                                          "kotlin.Comparable",
                                                                                                          "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase",
                                                                                                          "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase\$Static",
                                                                                                          "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase.Static"))),
                                                                                                    supertypes = listOf(
                                                                                                      "java.lang.Comparable",
                                                                                                      "kotlin.Comparable"))),
                                              withDefault = false),
                          OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "projectPath",
                                              valueType = primitiveTypeStringNotNullable, withDefault = false),
                          OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "virtualFileUrl",
                                              valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = true,
                                                                                                  typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                    fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                                              withDefault = false)),
      supertypes = listOf("com.intellij.platform.workspace.storage.EntitySource",
                          "org.jetbrains.plugins.gradle.service.syncAction.GradleEntitySource"))

    addMetadata(typeMetadata)

    typeMetadata = FinalClassMetadata.ClassMetadata(
      fqName = "org.jetbrains.plugins.gradle.service.syncAction.impl.contributors.GradleContentRootSyncContributor\$GradleContentRootEntitySource",
      properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "phase",
                                              valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                  typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(
                                                                                                    fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase",
                                                                                                    subclasses = listOf(
                                                                                                      FinalClassMetadata.ClassMetadata(
                                                                                                        fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleDynamicSyncPhase",
                                                                                                        properties = listOf(
                                                                                                          OwnPropertyMetadata(
                                                                                                            isComputable = false,
                                                                                                            isKey = false, isOpen = false,
                                                                                                            name = "modelFetchPhase",
                                                                                                            valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                                              isNullable = false,
                                                                                                              typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(
                                                                                                                fqName = "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase",
                                                                                                                subclasses = listOf(
                                                                                                                  FinalClassMetadata.ClassMetadata(
                                                                                                                    fqName = "com.intellij.gradle.toolingExtension.modelAction.GradleBuildFinishedModelFetchPhase",
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
                                                                                                                        name = "order",
                                                                                                                        valueType = primitiveTypeIntNotNullable,
                                                                                                                        withDefault = false)),
                                                                                                                    supertypes = listOf(
                                                                                                                      "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase",
                                                                                                                      "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase\$BuildFinished",
                                                                                                                      "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase.BuildFinished",
                                                                                                                      "java.io.Serializable",
                                                                                                                      "kotlin.Comparable")),
                                                                                                                  FinalClassMetadata.ClassMetadata(
                                                                                                                    fqName = "com.intellij.gradle.toolingExtension.modelAction.GradleProjectLoadedModelFetchPhase",
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
                                                                                                                        name = "order",
                                                                                                                        valueType = primitiveTypeIntNotNullable,
                                                                                                                        withDefault = false)),
                                                                                                                    supertypes = listOf(
                                                                                                                      "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase",
                                                                                                                      "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase\$ProjectLoaded",
                                                                                                                      "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase.ProjectLoaded",
                                                                                                                      "java.io.Serializable",
                                                                                                                      "kotlin.Comparable"))),
                                                                                                                supertypes = listOf(
                                                                                                                  "java.io.Serializable",
                                                                                                                  "java.lang.Comparable",
                                                                                                                  "kotlin.Comparable"))),
                                                                                                            withDefault = false),
                                                                                                          OwnPropertyMetadata(
                                                                                                            isComputable = false,
                                                                                                            isKey = false, isOpen = false,
                                                                                                            name = "name",
                                                                                                            valueType = primitiveTypeStringNotNullable,
                                                                                                            withDefault = false)),
                                                                                                        supertypes = listOf(
                                                                                                          "kotlin.Comparable",
                                                                                                          "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase",
                                                                                                          "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase\$Dynamic",
                                                                                                          "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase.Dynamic")),
                                                                                                      FinalClassMetadata.ClassMetadata(
                                                                                                        fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleStaticSyncPhase",
                                                                                                        properties = listOf(
                                                                                                          OwnPropertyMetadata(
                                                                                                            isComputable = false,
                                                                                                            isKey = false, isOpen = false,
                                                                                                            name = "name",
                                                                                                            valueType = primitiveTypeStringNotNullable,
                                                                                                            withDefault = false),
                                                                                                          OwnPropertyMetadata(
                                                                                                            isComputable = false,
                                                                                                            isKey = false, isOpen = false,
                                                                                                            name = "order",
                                                                                                            valueType = primitiveTypeIntNotNullable,
                                                                                                            withDefault = false)),
                                                                                                        supertypes = listOf(
                                                                                                          "kotlin.Comparable",
                                                                                                          "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase",
                                                                                                          "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase\$Static",
                                                                                                          "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase.Static"))),
                                                                                                    supertypes = listOf(
                                                                                                      "java.lang.Comparable",
                                                                                                      "kotlin.Comparable"))),
                                              withDefault = false),
                          OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "projectPath",
                                              valueType = primitiveTypeStringNotNullable, withDefault = false),
                          OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "virtualFileUrl",
                                              valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = true,
                                                                                                  typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                    fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                                              withDefault = false)),
      supertypes = listOf("com.intellij.platform.workspace.storage.EntitySource",
                          "org.jetbrains.plugins.gradle.service.syncAction.GradleEntitySource",
                          "org.jetbrains.plugins.gradle.service.syncAction.impl.bridge.GradleBridgeEntitySource"))

    addMetadata(typeMetadata)

    typeMetadata = FinalClassMetadata.ClassMetadata(
      fqName = "org.jetbrains.plugins.gradle.service.syncAction.impl.contributors.GradleContentRootSyncContributor\$GradleProjectModelEntitySource",
      properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "phase",
                                              valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                  typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(
                                                                                                    fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase",
                                                                                                    subclasses = listOf(
                                                                                                      FinalClassMetadata.ClassMetadata(
                                                                                                        fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleDynamicSyncPhase",
                                                                                                        properties = listOf(
                                                                                                          OwnPropertyMetadata(
                                                                                                            isComputable = false,
                                                                                                            isKey = false, isOpen = false,
                                                                                                            name = "modelFetchPhase",
                                                                                                            valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                                              isNullable = false,
                                                                                                              typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(
                                                                                                                fqName = "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase",
                                                                                                                subclasses = listOf(
                                                                                                                  FinalClassMetadata.ClassMetadata(
                                                                                                                    fqName = "com.intellij.gradle.toolingExtension.modelAction.GradleBuildFinishedModelFetchPhase",
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
                                                                                                                        name = "order",
                                                                                                                        valueType = primitiveTypeIntNotNullable,
                                                                                                                        withDefault = false)),
                                                                                                                    supertypes = listOf(
                                                                                                                      "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase",
                                                                                                                      "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase\$BuildFinished",
                                                                                                                      "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase.BuildFinished",
                                                                                                                      "java.io.Serializable",
                                                                                                                      "kotlin.Comparable")),
                                                                                                                  FinalClassMetadata.ClassMetadata(
                                                                                                                    fqName = "com.intellij.gradle.toolingExtension.modelAction.GradleProjectLoadedModelFetchPhase",
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
                                                                                                                        name = "order",
                                                                                                                        valueType = primitiveTypeIntNotNullable,
                                                                                                                        withDefault = false)),
                                                                                                                    supertypes = listOf(
                                                                                                                      "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase",
                                                                                                                      "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase\$ProjectLoaded",
                                                                                                                      "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase.ProjectLoaded",
                                                                                                                      "java.io.Serializable",
                                                                                                                      "kotlin.Comparable"))),
                                                                                                                supertypes = listOf(
                                                                                                                  "java.io.Serializable",
                                                                                                                  "java.lang.Comparable",
                                                                                                                  "kotlin.Comparable"))),
                                                                                                            withDefault = false),
                                                                                                          OwnPropertyMetadata(
                                                                                                            isComputable = false,
                                                                                                            isKey = false, isOpen = false,
                                                                                                            name = "name",
                                                                                                            valueType = primitiveTypeStringNotNullable,
                                                                                                            withDefault = false)),
                                                                                                        supertypes = listOf(
                                                                                                          "kotlin.Comparable",
                                                                                                          "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase",
                                                                                                          "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase\$Dynamic",
                                                                                                          "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase.Dynamic")),
                                                                                                      FinalClassMetadata.ClassMetadata(
                                                                                                        fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleStaticSyncPhase",
                                                                                                        properties = listOf(
                                                                                                          OwnPropertyMetadata(
                                                                                                            isComputable = false,
                                                                                                            isKey = false, isOpen = false,
                                                                                                            name = "name",
                                                                                                            valueType = primitiveTypeStringNotNullable,
                                                                                                            withDefault = false),
                                                                                                          OwnPropertyMetadata(
                                                                                                            isComputable = false,
                                                                                                            isKey = false, isOpen = false,
                                                                                                            name = "order",
                                                                                                            valueType = primitiveTypeIntNotNullable,
                                                                                                            withDefault = false)),
                                                                                                        supertypes = listOf(
                                                                                                          "kotlin.Comparable",
                                                                                                          "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase",
                                                                                                          "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase\$Static",
                                                                                                          "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase.Static"))),
                                                                                                    supertypes = listOf(
                                                                                                      "java.lang.Comparable",
                                                                                                      "kotlin.Comparable"))),
                                              withDefault = false),
                          OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "projectPath",
                                              valueType = primitiveTypeStringNotNullable, withDefault = false),
                          OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "virtualFileUrl",
                                              valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = true,
                                                                                                  typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                    fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                                              withDefault = false)),
      supertypes = listOf("com.intellij.platform.workspace.storage.EntitySource",
                          "org.jetbrains.plugins.gradle.service.syncAction.GradleEntitySource"))

    addMetadata(typeMetadata)

    typeMetadata = FinalClassMetadata.ClassMetadata(
      fqName = "org.jetbrains.plugins.gradle.service.syncAction.impl.contributors.GradleProjectRootSyncContributor\$GradleProjectRootEntitySource",
      properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "phase",
                                              valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                  typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(
                                                                                                    fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase",
                                                                                                    subclasses = listOf(
                                                                                                      FinalClassMetadata.ClassMetadata(
                                                                                                        fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleDynamicSyncPhase",
                                                                                                        properties = listOf(
                                                                                                          OwnPropertyMetadata(
                                                                                                            isComputable = false,
                                                                                                            isKey = false, isOpen = false,
                                                                                                            name = "modelFetchPhase",
                                                                                                            valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                                              isNullable = false,
                                                                                                              typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(
                                                                                                                fqName = "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase",
                                                                                                                subclasses = listOf(
                                                                                                                  FinalClassMetadata.ClassMetadata(
                                                                                                                    fqName = "com.intellij.gradle.toolingExtension.modelAction.GradleBuildFinishedModelFetchPhase",
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
                                                                                                                        name = "order",
                                                                                                                        valueType = primitiveTypeIntNotNullable,
                                                                                                                        withDefault = false)),
                                                                                                                    supertypes = listOf(
                                                                                                                      "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase",
                                                                                                                      "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase\$BuildFinished",
                                                                                                                      "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase.BuildFinished",
                                                                                                                      "java.io.Serializable",
                                                                                                                      "kotlin.Comparable")),
                                                                                                                  FinalClassMetadata.ClassMetadata(
                                                                                                                    fqName = "com.intellij.gradle.toolingExtension.modelAction.GradleProjectLoadedModelFetchPhase",
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
                                                                                                                        name = "order",
                                                                                                                        valueType = primitiveTypeIntNotNullable,
                                                                                                                        withDefault = false)),
                                                                                                                    supertypes = listOf(
                                                                                                                      "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase",
                                                                                                                      "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase\$ProjectLoaded",
                                                                                                                      "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase.ProjectLoaded",
                                                                                                                      "java.io.Serializable",
                                                                                                                      "kotlin.Comparable"))),
                                                                                                                supertypes = listOf(
                                                                                                                  "java.io.Serializable",
                                                                                                                  "java.lang.Comparable",
                                                                                                                  "kotlin.Comparable"))),
                                                                                                            withDefault = false),
                                                                                                          OwnPropertyMetadata(
                                                                                                            isComputable = false,
                                                                                                            isKey = false, isOpen = false,
                                                                                                            name = "name",
                                                                                                            valueType = primitiveTypeStringNotNullable,
                                                                                                            withDefault = false)),
                                                                                                        supertypes = listOf(
                                                                                                          "kotlin.Comparable",
                                                                                                          "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase",
                                                                                                          "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase\$Dynamic",
                                                                                                          "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase.Dynamic")),
                                                                                                      FinalClassMetadata.ClassMetadata(
                                                                                                        fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleStaticSyncPhase",
                                                                                                        properties = listOf(
                                                                                                          OwnPropertyMetadata(
                                                                                                            isComputable = false,
                                                                                                            isKey = false, isOpen = false,
                                                                                                            name = "name",
                                                                                                            valueType = primitiveTypeStringNotNullable,
                                                                                                            withDefault = false),
                                                                                                          OwnPropertyMetadata(
                                                                                                            isComputable = false,
                                                                                                            isKey = false, isOpen = false,
                                                                                                            name = "order",
                                                                                                            valueType = primitiveTypeIntNotNullable,
                                                                                                            withDefault = false)),
                                                                                                        supertypes = listOf(
                                                                                                          "kotlin.Comparable",
                                                                                                          "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase",
                                                                                                          "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase\$Static",
                                                                                                          "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase.Static"))),
                                                                                                    supertypes = listOf(
                                                                                                      "java.lang.Comparable",
                                                                                                      "kotlin.Comparable"))),
                                              withDefault = false),
                          OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "projectPath",
                                              valueType = primitiveTypeStringNotNullable, withDefault = false),
                          OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "virtualFileUrl",
                                              valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = true,
                                                                                                  typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                    fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                                              withDefault = false)),
      supertypes = listOf("com.intellij.platform.workspace.storage.EntitySource",
                          "org.jetbrains.plugins.gradle.service.syncAction.GradleEntitySource",
                          "org.jetbrains.plugins.gradle.service.syncAction.impl.bridge.GradleBridgeEntitySource"))

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "org.jetbrains.plugins.gradle.model.projectModel.GradleBuildEntity",
                                  entityDataFqName = "org.jetbrains.plugins.gradle.model.projectModel.impl.GradleBuildEntityData",
                                  supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity",
                                                      "com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId"),
                                  properties = listOf(
                                    OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource",
                                                        valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                            typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                              fqName = "com.intellij.platform.workspace.storage.EntitySource")),
                                                        withDefault = false),
                                    OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "externalProject",
                                                        valueType = ValueTypeMetadata.EntityReference(
                                                          connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                          entityFqName = "com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntity",
                                                          isChild = false, isNullable = false), withDefault = false),
                                    OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "externalProjectId",
                                                        valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                            typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                              fqName = "com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntityId",
                                                                                                              properties = listOf(
                                                                                                                OwnPropertyMetadata(
                                                                                                                  isComputable = false,
                                                                                                                  isKey = false,
                                                                                                                  isOpen = false,
                                                                                                                  name = "codeCache",
                                                                                                                  valueType = primitiveTypeIntNotNullable,
                                                                                                                  withDefault = false),
                                                                                                                OwnPropertyMetadata(
                                                                                                                  isComputable = false,
                                                                                                                  isKey = false,
                                                                                                                  isOpen = false,
                                                                                                                  name = "externalProjectPath",
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
                                    OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "name",
                                                        valueType = primitiveTypeStringNotNullable, withDefault = false),
                                    OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "url",
                                                        valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                            typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                              fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                                                        withDefault = false),
                                    OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "projects",
                                                        valueType = ValueTypeMetadata.EntityReference(
                                                          connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                          entityFqName = "org.jetbrains.plugins.gradle.model.projectModel.GradleProjectEntity",
                                                          isChild = true, isNullable = false), withDefault = false),
                                    OwnPropertyMetadata(isComputable = true, isKey = false, isOpen = false, name = "symbolicId",
                                                        valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                            typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                              fqName = "org.jetbrains.plugins.gradle.model.projectModel.GradleBuildEntityId",
                                                                                                              properties = listOf(
                                                                                                                OwnPropertyMetadata(
                                                                                                                  isComputable = false,
                                                                                                                  isKey = false,
                                                                                                                  isOpen = false,
                                                                                                                  name = "codeCache",
                                                                                                                  valueType = primitiveTypeIntNotNullable,
                                                                                                                  withDefault = false),
                                                                                                                OwnPropertyMetadata(
                                                                                                                  isComputable = false,
                                                                                                                  isKey = false,
                                                                                                                  isOpen = false,
                                                                                                                  name = "externalProjectId",
                                                                                                                  valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                                                    isNullable = false,
                                                                                                                    typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                      fqName = "com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntityId",
                                                                                                                      properties = listOf(
                                                                                                                        OwnPropertyMetadata(
                                                                                                                          isComputable = false,
                                                                                                                          isKey = false,
                                                                                                                          isOpen = false,
                                                                                                                          name = "codeCache",
                                                                                                                          valueType = primitiveTypeIntNotNullable,
                                                                                                                          withDefault = false),
                                                                                                                        OwnPropertyMetadata(
                                                                                                                          isComputable = false,
                                                                                                                          isKey = false,
                                                                                                                          isOpen = false,
                                                                                                                          name = "externalProjectPath",
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
                                                                                                                  withDefault = false),
                                                                                                                OwnPropertyMetadata(
                                                                                                                  isComputable = false,
                                                                                                                  isKey = false,
                                                                                                                  isOpen = false,
                                                                                                                  name = "url",
                                                                                                                  valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                                                    isNullable = false,
                                                                                                                    typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                      fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                                                                                                                  withDefault = false)),
                                                                                                              supertypes = listOf(
                                                                                                                "com.intellij.platform.workspace.storage.SymbolicEntityId"))),
                                                        withDefault = false)), extProperties = listOf(
        ExtPropertyMetadata(isComputable = false, isOpen = false, name = "gradleBuilds",
                            receiverFqn = "com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntity",
                            valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                          entityFqName = "org.jetbrains.plugins.gradle.model.projectModel.GradleBuildEntity",
                                                                          isChild = true, isNullable = false), withDefault = false)),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "org.jetbrains.plugins.gradle.model.projectModel.GradleModuleEntity",
                                  entityDataFqName = "org.jetbrains.plugins.gradle.model.projectModel.impl.GradleModuleEntityData",
                                  supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity"), properties = listOf(
        OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource",
                            valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                typeMetadata = FinalClassMetadata.KnownClass(
                                                                                  fqName = "com.intellij.platform.workspace.storage.EntitySource")),
                            withDefault = false),
        OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "module",
                            valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                          entityFqName = "com.intellij.platform.workspace.jps.entities.ModuleEntity",
                                                                          isChild = false, isNullable = false), withDefault = false),
        OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "gradleProjectId",
                            valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                  fqName = "org.jetbrains.plugins.gradle.model.projectModel.GradleProjectEntityId",
                                                                                  properties = listOf(
                                                                                    OwnPropertyMetadata(isComputable = false, isKey = false,
                                                                                                        isOpen = false, name = "buildId",
                                                                                                        valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                                          isNullable = false,
                                                                                                          typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                            fqName = "org.jetbrains.plugins.gradle.model.projectModel.GradleBuildEntityId",
                                                                                                            properties = listOf(
                                                                                                              OwnPropertyMetadata(
                                                                                                                isComputable = false,
                                                                                                                isKey = false,
                                                                                                                isOpen = false,
                                                                                                                name = "codeCache",
                                                                                                                valueType = primitiveTypeIntNotNullable,
                                                                                                                withDefault = false),
                                                                                                              OwnPropertyMetadata(
                                                                                                                isComputable = false,
                                                                                                                isKey = false,
                                                                                                                isOpen = false,
                                                                                                                name = "externalProjectId",
                                                                                                                valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                                                  isNullable = false,
                                                                                                                  typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                    fqName = "com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntityId",
                                                                                                                    properties = listOf(
                                                                                                                      OwnPropertyMetadata(
                                                                                                                        isComputable = false,
                                                                                                                        isKey = false,
                                                                                                                        isOpen = false,
                                                                                                                        name = "codeCache",
                                                                                                                        valueType = primitiveTypeIntNotNullable,
                                                                                                                        withDefault = false),
                                                                                                                      OwnPropertyMetadata(
                                                                                                                        isComputable = false,
                                                                                                                        isKey = false,
                                                                                                                        isOpen = false,
                                                                                                                        name = "externalProjectPath",
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
                                                                                                                withDefault = false),
                                                                                                              OwnPropertyMetadata(
                                                                                                                isComputable = false,
                                                                                                                isKey = false,
                                                                                                                isOpen = false,
                                                                                                                name = "url",
                                                                                                                valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                                                  isNullable = false,
                                                                                                                  typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                    fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                                                                                                                withDefault = false)),
                                                                                                            supertypes = listOf(
                                                                                                              "com.intellij.platform.workspace.storage.SymbolicEntityId"))),
                                                                                                        withDefault = false),
                                                                                    OwnPropertyMetadata(isComputable = false, isKey = false,
                                                                                                        isOpen = false, name = "codeCache",
                                                                                                        valueType = primitiveTypeIntNotNullable,
                                                                                                        withDefault = false),
                                                                                    OwnPropertyMetadata(isComputable = false, isKey = false,
                                                                                                        isOpen = false,
                                                                                                        name = "presentableName",
                                                                                                        valueType = primitiveTypeStringNotNullable,
                                                                                                        withDefault = false),
                                                                                    OwnPropertyMetadata(isComputable = false, isKey = false,
                                                                                                        isOpen = false, name = "url",
                                                                                                        valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                                          isNullable = false,
                                                                                                          typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                            fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                                                                                                        withDefault = false)),
                                                                                  supertypes = listOf(
                                                                                    "com.intellij.platform.workspace.storage.SymbolicEntityId"))),
                            withDefault = false)), extProperties = listOf(
        ExtPropertyMetadata(isComputable = false, isOpen = false, name = "gradleModuleEntity",
                            receiverFqn = "com.intellij.platform.workspace.jps.entities.ModuleEntity",
                            valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                          entityFqName = "org.jetbrains.plugins.gradle.model.projectModel.GradleModuleEntity",
                                                                          isChild = true, isNullable = true), withDefault = false)),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "org.jetbrains.plugins.gradle.model.projectModel.GradleProjectEntity",
                                  entityDataFqName = "org.jetbrains.plugins.gradle.model.projectModel.impl.GradleProjectEntityData",
                                  supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity",
                                                      "com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId"),
                                  properties = listOf(
                                    OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource",
                                                        valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                            typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                              fqName = "com.intellij.platform.workspace.storage.EntitySource")),
                                                        withDefault = false),
                                    OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "build",
                                                        valueType = ValueTypeMetadata.EntityReference(
                                                          connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                          entityFqName = "org.jetbrains.plugins.gradle.model.projectModel.GradleBuildEntity",
                                                          isChild = false, isNullable = false), withDefault = false),
                                    OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "buildId",
                                                        valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                            typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                              fqName = "org.jetbrains.plugins.gradle.model.projectModel.GradleBuildEntityId",
                                                                                                              properties = listOf(
                                                                                                                OwnPropertyMetadata(
                                                                                                                  isComputable = false,
                                                                                                                  isKey = false,
                                                                                                                  isOpen = false,
                                                                                                                  name = "codeCache",
                                                                                                                  valueType = primitiveTypeIntNotNullable,
                                                                                                                  withDefault = false),
                                                                                                                OwnPropertyMetadata(
                                                                                                                  isComputable = false,
                                                                                                                  isKey = false,
                                                                                                                  isOpen = false,
                                                                                                                  name = "externalProjectId",
                                                                                                                  valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                                                    isNullable = false,
                                                                                                                    typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                      fqName = "com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntityId",
                                                                                                                      properties = listOf(
                                                                                                                        OwnPropertyMetadata(
                                                                                                                          isComputable = false,
                                                                                                                          isKey = false,
                                                                                                                          isOpen = false,
                                                                                                                          name = "codeCache",
                                                                                                                          valueType = primitiveTypeIntNotNullable,
                                                                                                                          withDefault = false),
                                                                                                                        OwnPropertyMetadata(
                                                                                                                          isComputable = false,
                                                                                                                          isKey = false,
                                                                                                                          isOpen = false,
                                                                                                                          name = "externalProjectPath",
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
                                                                                                                  withDefault = false),
                                                                                                                OwnPropertyMetadata(
                                                                                                                  isComputable = false,
                                                                                                                  isKey = false,
                                                                                                                  isOpen = false,
                                                                                                                  name = "url",
                                                                                                                  valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                                                    isNullable = false,
                                                                                                                    typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                      fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                                                                                                                  withDefault = false)),
                                                                                                              supertypes = listOf(
                                                                                                                "com.intellij.platform.workspace.storage.SymbolicEntityId"))),
                                                        withDefault = false),
                                    OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "name",
                                                        valueType = primitiveTypeStringNotNullable, withDefault = false),
                                    OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "path",
                                                        valueType = primitiveTypeStringNotNullable, withDefault = false),
                                    OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "identityPath",
                                                        valueType = primitiveTypeStringNotNullable, withDefault = false),
                                    OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "url",
                                                        valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                            typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                              fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                                                        withDefault = false),
                                    OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "linkedProjectId",
                                                        valueType = primitiveTypeStringNotNullable, withDefault = false),
                                    OwnPropertyMetadata(isComputable = true, isKey = false, isOpen = false, name = "symbolicId",
                                                        valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                            typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                              fqName = "org.jetbrains.plugins.gradle.model.projectModel.GradleProjectEntityId",
                                                                                                              properties = listOf(
                                                                                                                OwnPropertyMetadata(
                                                                                                                  isComputable = false,
                                                                                                                  isKey = false,
                                                                                                                  isOpen = false,
                                                                                                                  name = "buildId",
                                                                                                                  valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                                                    isNullable = false,
                                                                                                                    typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                      fqName = "org.jetbrains.plugins.gradle.model.projectModel.GradleBuildEntityId",
                                                                                                                      properties = listOf(
                                                                                                                        OwnPropertyMetadata(
                                                                                                                          isComputable = false,
                                                                                                                          isKey = false,
                                                                                                                          isOpen = false,
                                                                                                                          name = "codeCache",
                                                                                                                          valueType = primitiveTypeIntNotNullable,
                                                                                                                          withDefault = false),
                                                                                                                        OwnPropertyMetadata(
                                                                                                                          isComputable = false,
                                                                                                                          isKey = false,
                                                                                                                          isOpen = false,
                                                                                                                          name = "externalProjectId",
                                                                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                                                            isNullable = false,
                                                                                                                            typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                              fqName = "com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntityId",
                                                                                                                              properties = listOf(
                                                                                                                                OwnPropertyMetadata(
                                                                                                                                  isComputable = false,
                                                                                                                                  isKey = false,
                                                                                                                                  isOpen = false,
                                                                                                                                  name = "codeCache",
                                                                                                                                  valueType = primitiveTypeIntNotNullable,
                                                                                                                                  withDefault = false),
                                                                                                                                OwnPropertyMetadata(
                                                                                                                                  isComputable = false,
                                                                                                                                  isKey = false,
                                                                                                                                  isOpen = false,
                                                                                                                                  name = "externalProjectPath",
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
                                                                                                                          withDefault = false),
                                                                                                                        OwnPropertyMetadata(
                                                                                                                          isComputable = false,
                                                                                                                          isKey = false,
                                                                                                                          isOpen = false,
                                                                                                                          name = "url",
                                                                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                                                            isNullable = false,
                                                                                                                            typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                              fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                                                                                                                          withDefault = false)),
                                                                                                                      supertypes = listOf(
                                                                                                                        "com.intellij.platform.workspace.storage.SymbolicEntityId"))),
                                                                                                                  withDefault = false),
                                                                                                                OwnPropertyMetadata(
                                                                                                                  isComputable = false,
                                                                                                                  isKey = false,
                                                                                                                  isOpen = false,
                                                                                                                  name = "codeCache",
                                                                                                                  valueType = primitiveTypeIntNotNullable,
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
                                                                                                                  name = "url",
                                                                                                                  valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                                                    isNullable = false,
                                                                                                                    typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                      fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                                                                                                                  withDefault = false)),
                                                                                                              supertypes = listOf(
                                                                                                                "com.intellij.platform.workspace.storage.SymbolicEntityId"))),
                                                        withDefault = false)), extProperties = listOf(), isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "org.jetbrains.plugins.gradle.model.versionCatalogs.GradleVersionCatalogEntity",
                                  entityDataFqName = "org.jetbrains.plugins.gradle.model.versionCatalogs.impl.GradleVersionCatalogEntityData",
                                  supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity"), properties = listOf(
        OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource",
                            valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                typeMetadata = FinalClassMetadata.KnownClass(
                                                                                  fqName = "com.intellij.platform.workspace.storage.EntitySource")),
                            withDefault = false),
        OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "name", valueType = primitiveTypeStringNotNullable,
                            withDefault = false),
        OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "url",
                            valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                typeMetadata = FinalClassMetadata.KnownClass(
                                                                                  fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                            withDefault = false),
        OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "build",
                            valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                          entityFqName = "org.jetbrains.plugins.gradle.model.projectModel.GradleBuildEntity",
                                                                          isChild = false, isNullable = false), withDefault = false)),
                                  extProperties = listOf(ExtPropertyMetadata(isComputable = false, isOpen = false, name = "versionCatalogs",
                                                                             receiverFqn = "org.jetbrains.plugins.gradle.model.projectModel.GradleBuildEntity",
                                                                             valueType = ValueTypeMetadata.EntityReference(
                                                                               connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                               entityFqName = "org.jetbrains.plugins.gradle.model.versionCatalogs.GradleVersionCatalogEntity",
                                                                               isChild = true, isNullable = false), withDefault = false)),
                                  isAbstract = false)

    addMetadata(typeMetadata)
  }

  override fun initializeMetadataHash() {
    addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.model.projectModel.GradleBuildEntity", metadataHash = 261393341)
    addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.model.projectModel.GradleModuleEntity", metadataHash = 657314474)
    addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.model.projectModel.GradleProjectEntity", metadataHash = 547852071)
    addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.model.versionCatalogs.GradleVersionCatalogEntity", metadataHash = 1998822139)
    addMetadataHash(typeFqn = "com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntityId", metadataHash = -535054241)
    addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.model.projectModel.GradleBuildEntityId", metadataHash = -1846600137)
    addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.model.projectModel.GradleProjectEntityId", metadataHash = -1029413002)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.SymbolicEntityId", metadataHash = 39125357)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.EntitySource", metadataHash = 361104699)
    addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.service.syncAction.GradleEntitySource", metadataHash = -565169080)
    addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.service.syncAction.impl.bridge.GradleBridgeEntitySource",
                    metadataHash = 1893250484)
    addMetadataHash(
      typeFqn = "org.jetbrains.plugins.gradle.service.syncAction.impl.bridge.GradleBridgeModuleDataService\$GradleBridgeModuleEntitySource",
      metadataHash = -118266230)
    addMetadataHash(
      typeFqn = "org.jetbrains.plugins.gradle.service.syncAction.impl.contributors.GradleContentRootSyncContributor\$GradleContentRootEntitySource",
      metadataHash = -1866518801)
    addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase", metadataHash = 6939851)
    addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase\$Dynamic", metadataHash = 896961146)
    addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.service.syncAction.GradleDynamicSyncPhase", metadataHash = 739426399)
    addMetadataHash(typeFqn = "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase", metadataHash = 1617061511)
    addMetadataHash(typeFqn = "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase\$BuildFinished",
                    metadataHash = -384742708)
    addMetadataHash(typeFqn = "com.intellij.gradle.toolingExtension.modelAction.GradleBuildFinishedModelFetchPhase",
                    metadataHash = 1680822981)
    addMetadataHash(typeFqn = "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase\$ProjectLoaded",
                    metadataHash = -38038196)
    addMetadataHash(typeFqn = "com.intellij.gradle.toolingExtension.modelAction.GradleProjectLoadedModelFetchPhase",
                    metadataHash = 1467460995)
    addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase\$Static", metadataHash = -1225530548)
    addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.service.syncAction.GradleStaticSyncPhase", metadataHash = 79572120)
    addMetadataHash(
      typeFqn = "org.jetbrains.plugins.gradle.service.syncAction.impl.contributors.GradleContentRootSyncContributor\$GradleProjectModelEntitySource",
      metadataHash = -1901195304)
    addMetadataHash(
      typeFqn = "org.jetbrains.plugins.gradle.service.syncAction.impl.contributors.GradleProjectRootSyncContributor\$GradleProjectRootEntitySource",
      metadataHash = -530336721)
    addMetadataHash(
      typeFqn = "org.jetbrains.plugins.gradle.service.syncAction.impl.contributors.GradleVersionCatalogSyncContributor\$GradleVersionCatalogEntitySource",
      metadataHash = -947119701)
  }

}
