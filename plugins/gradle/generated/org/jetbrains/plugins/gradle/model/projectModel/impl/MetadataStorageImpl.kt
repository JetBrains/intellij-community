// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
                                                    properties = listOf(OwnPropertyMetadata(isComputable = false,
                                                                                            isKey = false,
                                                                                            isOpen = false,
                                                                                            name = "codeCache",
                                                                                            valueType = primitiveTypeIntNotNullable,
                                                                                            withDefault = false),
                                                                        OwnPropertyMetadata(isComputable = false,
                                                                                            isKey = false,
                                                                                            isOpen = false,
                                                                                            name = "externalProjectId",
                                                                                            valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                              isNullable = false,
                                                                                              typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                fqName = "com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntityId",
                                                                                                properties = listOf(OwnPropertyMetadata(
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
                                                                                                supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId"))),
                                                                                            withDefault = false),
                                                                        OwnPropertyMetadata(isComputable = false,
                                                                                            isKey = false,
                                                                                            isOpen = false,
                                                                                            name = "presentableName",
                                                                                            valueType = primitiveTypeStringNotNullable,
                                                                                            withDefault = false),
                                                                        OwnPropertyMetadata(isComputable = false,
                                                                                            isKey = false,
                                                                                            isOpen = false,
                                                                                            name = "url",
                                                                                            valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                              isNullable = false,
                                                                                              typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                                                                                            withDefault = false)),
                                                    supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId"))

    addMetadata(typeMetadata)

    typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "org.jetbrains.plugins.gradle.model.projectModel.GradleProjectEntityId",
                                                    properties = listOf(OwnPropertyMetadata(isComputable = false,
                                                                                            isKey = false,
                                                                                            isOpen = false,
                                                                                            name = "buildId",
                                                                                            valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                              isNullable = false,
                                                                                              typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                fqName = "org.jetbrains.plugins.gradle.model.projectModel.GradleBuildEntityId",
                                                                                                properties = listOf(OwnPropertyMetadata(
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
                                                                                                supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId"))),
                                                                                            withDefault = false),
                                                                        OwnPropertyMetadata(isComputable = false,
                                                                                            isKey = false,
                                                                                            isOpen = false,
                                                                                            name = "codeCache",
                                                                                            valueType = primitiveTypeIntNotNullable,
                                                                                            withDefault = false),
                                                                        OwnPropertyMetadata(isComputable = false,
                                                                                            isKey = false,
                                                                                            isOpen = false,
                                                                                            name = "identityPath",
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
      FinalClassMetadata.ClassMetadata(fqName = "org.jetbrains.plugins.gradle.service.syncAction.impl.bridge.GradleBridgeFinalizerDataService\$DataServiceEntitySource",
                                       properties = listOf(OwnPropertyMetadata(isComputable = false,
                                                                               isKey = false,
                                                                               isOpen = false,
                                                                               name = "phase",
                                                                               valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                 isNullable = false,
                                                                                 typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(
                                                                                   fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase",
                                                                                   subclasses = listOf(FinalClassMetadata.ObjectMetadata(
                                                                                     fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleBaseScriptSyncPhase",
                                                                                     properties = listOf(OwnPropertyMetadata(isComputable = false,
                                                                                                                             isKey = false,
                                                                                                                             isOpen = false,
                                                                                                                             name = "name",
                                                                                                                             valueType = primitiveTypeStringNotNullable,
                                                                                                                             withDefault = false)),
                                                                                     supertypes = listOf("kotlin.Comparable",
                                                                                                         "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase",
                                                                                                         "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase\$BaseScript",
                                                                                                         "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase.BaseScript")),
                                                                                                       FinalClassMetadata.ClassMetadata(
                                                                                                         fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleDynamicSyncPhase",
                                                                                                         properties = listOf(
                                                                                                           OwnPropertyMetadata(isComputable = false,
                                                                                                                               isKey = false,
                                                                                                                               isOpen = false,
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
                                                                                                           OwnPropertyMetadata(isComputable = false,
                                                                                                                               isKey = false,
                                                                                                                               isOpen = false,
                                                                                                                               name = "name",
                                                                                                                               valueType = primitiveTypeStringNotNullable,
                                                                                                                               withDefault = false)),
                                                                                                         supertypes = listOf("kotlin.Comparable",
                                                                                                                             "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase",
                                                                                                                             "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase\$Dynamic",
                                                                                                                             "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase.Dynamic")),
                                                                                                       FinalClassMetadata.ClassMetadata(
                                                                                                         fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleDataServicesSyncPhase",
                                                                                                         properties = listOf(
                                                                                                           OwnPropertyMetadata(isComputable = false,
                                                                                                                               isKey = false,
                                                                                                                               isOpen = false,
                                                                                                                               name = "name",
                                                                                                                               valueType = primitiveTypeStringNotNullable,
                                                                                                                               withDefault = false)),
                                                                                                         supertypes = listOf("kotlin.Comparable",
                                                                                                                             "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase",
                                                                                                                             "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase\$DataServices",
                                                                                                                             "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase.DataServices")),
                                                                                                       FinalClassMetadata.ClassMetadata(
                                                                                                         fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleStaticSyncPhase",
                                                                                                         properties = listOf(
                                                                                                           OwnPropertyMetadata(isComputable = false,
                                                                                                                               isKey = false,
                                                                                                                               isOpen = false,
                                                                                                                               name = "name",
                                                                                                                               valueType = primitiveTypeStringNotNullable,
                                                                                                                               withDefault = false),
                                                                                                           OwnPropertyMetadata(isComputable = false,
                                                                                                                               isKey = false,
                                                                                                                               isOpen = false,
                                                                                                                               name = "order",
                                                                                                                               valueType = primitiveTypeIntNotNullable,
                                                                                                                               withDefault = false)),
                                                                                                         supertypes = listOf("kotlin.Comparable",
                                                                                                                             "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase",
                                                                                                                             "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase\$Static",
                                                                                                                             "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase.Static"))),
                                                                                   supertypes = listOf("java.lang.Comparable",
                                                                                                       "kotlin.Comparable"))),
                                                                               withDefault = false),
                                                           OwnPropertyMetadata(isComputable = false,
                                                                               isKey = false,
                                                                               isOpen = false,
                                                                               name = "projectPath",
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
                                       supertypes = listOf("com.intellij.platform.workspace.storage.EntitySource",
                                                           "org.jetbrains.plugins.gradle.service.syncAction.GradleEntitySource"))

    addMetadata(typeMetadata)

    typeMetadata =
      FinalClassMetadata.ClassMetadata(fqName = "org.jetbrains.plugins.gradle.service.syncAction.impl.bridge.GradleBridgeModuleDataService\$GradleBridgeModuleEntitySource",
                                       properties = listOf(OwnPropertyMetadata(isComputable = false,
                                                                               isKey = false,
                                                                               isOpen = false,
                                                                               name = "virtualFileUrl",
                                                                               valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                 isNullable = true,
                                                                                 typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                                                                               withDefault = false)),
                                       supertypes = listOf("com.intellij.platform.workspace.storage.EntitySource"))

    addMetadata(typeMetadata)

    typeMetadata =
      FinalClassMetadata.ClassMetadata(fqName = "org.jetbrains.plugins.gradle.service.syncAction.impl.contributors.GradleVersionCatalogSyncContributor\$GradleVersionCatalogEntitySource",
                                       properties = listOf(OwnPropertyMetadata(isComputable = false,
                                                                               isKey = false,
                                                                               isOpen = false,
                                                                               name = "phase",
                                                                               valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                 isNullable = false,
                                                                                 typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(
                                                                                   fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase",
                                                                                   subclasses = listOf(FinalClassMetadata.ObjectMetadata(
                                                                                     fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleBaseScriptSyncPhase",
                                                                                     properties = listOf(OwnPropertyMetadata(isComputable = false,
                                                                                                                             isKey = false,
                                                                                                                             isOpen = false,
                                                                                                                             name = "name",
                                                                                                                             valueType = primitiveTypeStringNotNullable,
                                                                                                                             withDefault = false)),
                                                                                     supertypes = listOf("kotlin.Comparable",
                                                                                                         "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase",
                                                                                                         "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase\$BaseScript",
                                                                                                         "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase.BaseScript")),
                                                                                                       FinalClassMetadata.ClassMetadata(
                                                                                                         fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleDynamicSyncPhase",
                                                                                                         properties = listOf(
                                                                                                           OwnPropertyMetadata(isComputable = false,
                                                                                                                               isKey = false,
                                                                                                                               isOpen = false,
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
                                                                                                           OwnPropertyMetadata(isComputable = false,
                                                                                                                               isKey = false,
                                                                                                                               isOpen = false,
                                                                                                                               name = "name",
                                                                                                                               valueType = primitiveTypeStringNotNullable,
                                                                                                                               withDefault = false)),
                                                                                                         supertypes = listOf("kotlin.Comparable",
                                                                                                                             "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase",
                                                                                                                             "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase\$Dynamic",
                                                                                                                             "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase.Dynamic")),
                                                                                                       FinalClassMetadata.ClassMetadata(
                                                                                                         fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleDataServicesSyncPhase",
                                                                                                         properties = listOf(
                                                                                                           OwnPropertyMetadata(isComputable = false,
                                                                                                                               isKey = false,
                                                                                                                               isOpen = false,
                                                                                                                               name = "name",
                                                                                                                               valueType = primitiveTypeStringNotNullable,
                                                                                                                               withDefault = false)),
                                                                                                         supertypes = listOf("kotlin.Comparable",
                                                                                                                             "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase",
                                                                                                                             "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase\$DataServices",
                                                                                                                             "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase.DataServices")),
                                                                                                       FinalClassMetadata.ClassMetadata(
                                                                                                         fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleStaticSyncPhase",
                                                                                                         properties = listOf(
                                                                                                           OwnPropertyMetadata(isComputable = false,
                                                                                                                               isKey = false,
                                                                                                                               isOpen = false,
                                                                                                                               name = "name",
                                                                                                                               valueType = primitiveTypeStringNotNullable,
                                                                                                                               withDefault = false),
                                                                                                           OwnPropertyMetadata(isComputable = false,
                                                                                                                               isKey = false,
                                                                                                                               isOpen = false,
                                                                                                                               name = "order",
                                                                                                                               valueType = primitiveTypeIntNotNullable,
                                                                                                                               withDefault = false)),
                                                                                                         supertypes = listOf("kotlin.Comparable",
                                                                                                                             "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase",
                                                                                                                             "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase\$Static",
                                                                                                                             "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase.Static"))),
                                                                                   supertypes = listOf("java.lang.Comparable",
                                                                                                       "kotlin.Comparable"))),
                                                                               withDefault = false),
                                                           OwnPropertyMetadata(isComputable = false,
                                                                               isKey = false,
                                                                               isOpen = false,
                                                                               name = "projectPath",
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
                                       supertypes = listOf("com.intellij.platform.workspace.storage.EntitySource",
                                                           "org.jetbrains.plugins.gradle.service.syncAction.GradleEntitySource"))

    addMetadata(typeMetadata)

    typeMetadata =
      FinalClassMetadata.ClassMetadata(fqName = "org.jetbrains.plugins.gradle.service.syncAction.impl.contributors.GradleContentRootSyncContributor\$GradleContentRootEntitySource",
                                       properties = listOf(OwnPropertyMetadata(isComputable = false,
                                                                               isKey = false,
                                                                               isOpen = false,
                                                                               name = "phase",
                                                                               valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                 isNullable = false,
                                                                                 typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(
                                                                                   fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase",
                                                                                   subclasses = listOf(FinalClassMetadata.ObjectMetadata(
                                                                                     fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleBaseScriptSyncPhase",
                                                                                     properties = listOf(OwnPropertyMetadata(isComputable = false,
                                                                                                                             isKey = false,
                                                                                                                             isOpen = false,
                                                                                                                             name = "name",
                                                                                                                             valueType = primitiveTypeStringNotNullable,
                                                                                                                             withDefault = false)),
                                                                                     supertypes = listOf("kotlin.Comparable",
                                                                                                         "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase",
                                                                                                         "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase\$BaseScript",
                                                                                                         "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase.BaseScript")),
                                                                                                       FinalClassMetadata.ClassMetadata(
                                                                                                         fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleDynamicSyncPhase",
                                                                                                         properties = listOf(
                                                                                                           OwnPropertyMetadata(isComputable = false,
                                                                                                                               isKey = false,
                                                                                                                               isOpen = false,
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
                                                                                                           OwnPropertyMetadata(isComputable = false,
                                                                                                                               isKey = false,
                                                                                                                               isOpen = false,
                                                                                                                               name = "name",
                                                                                                                               valueType = primitiveTypeStringNotNullable,
                                                                                                                               withDefault = false)),
                                                                                                         supertypes = listOf("kotlin.Comparable",
                                                                                                                             "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase",
                                                                                                                             "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase\$Dynamic",
                                                                                                                             "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase.Dynamic")),
                                                                                                       FinalClassMetadata.ClassMetadata(
                                                                                                         fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleDataServicesSyncPhase",
                                                                                                         properties = listOf(
                                                                                                           OwnPropertyMetadata(isComputable = false,
                                                                                                                               isKey = false,
                                                                                                                               isOpen = false,
                                                                                                                               name = "name",
                                                                                                                               valueType = primitiveTypeStringNotNullable,
                                                                                                                               withDefault = false)),
                                                                                                         supertypes = listOf("kotlin.Comparable",
                                                                                                                             "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase",
                                                                                                                             "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase\$DataServices",
                                                                                                                             "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase.DataServices")),
                                                                                                       FinalClassMetadata.ClassMetadata(
                                                                                                         fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleStaticSyncPhase",
                                                                                                         properties = listOf(
                                                                                                           OwnPropertyMetadata(isComputable = false,
                                                                                                                               isKey = false,
                                                                                                                               isOpen = false,
                                                                                                                               name = "name",
                                                                                                                               valueType = primitiveTypeStringNotNullable,
                                                                                                                               withDefault = false),
                                                                                                           OwnPropertyMetadata(isComputable = false,
                                                                                                                               isKey = false,
                                                                                                                               isOpen = false,
                                                                                                                               name = "order",
                                                                                                                               valueType = primitiveTypeIntNotNullable,
                                                                                                                               withDefault = false)),
                                                                                                         supertypes = listOf("kotlin.Comparable",
                                                                                                                             "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase",
                                                                                                                             "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase\$Static",
                                                                                                                             "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase.Static"))),
                                                                                   supertypes = listOf("java.lang.Comparable",
                                                                                                       "kotlin.Comparable"))),
                                                                               withDefault = false),
                                                           OwnPropertyMetadata(isComputable = false,
                                                                               isKey = false,
                                                                               isOpen = false,
                                                                               name = "projectPath",
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
                                       supertypes = listOf("com.intellij.platform.workspace.storage.EntitySource",
                                                           "org.jetbrains.plugins.gradle.service.syncAction.GradleEntitySource",
                                                           "org.jetbrains.plugins.gradle.service.syncAction.impl.bridge.GradleBridgeEntitySource"))

    addMetadata(typeMetadata)

    typeMetadata =
      FinalClassMetadata.ClassMetadata(fqName = "org.jetbrains.plugins.gradle.service.syncAction.impl.contributors.GradleContentRootSyncContributor\$GradleProjectModelEntitySource",
                                       properties = listOf(OwnPropertyMetadata(isComputable = false,
                                                                               isKey = false,
                                                                               isOpen = false,
                                                                               name = "phase",
                                                                               valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                 isNullable = false,
                                                                                 typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(
                                                                                   fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase",
                                                                                   subclasses = listOf(FinalClassMetadata.ObjectMetadata(
                                                                                     fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleBaseScriptSyncPhase",
                                                                                     properties = listOf(OwnPropertyMetadata(isComputable = false,
                                                                                                                             isKey = false,
                                                                                                                             isOpen = false,
                                                                                                                             name = "name",
                                                                                                                             valueType = primitiveTypeStringNotNullable,
                                                                                                                             withDefault = false)),
                                                                                     supertypes = listOf("kotlin.Comparable",
                                                                                                         "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase",
                                                                                                         "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase\$BaseScript",
                                                                                                         "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase.BaseScript")),
                                                                                                       FinalClassMetadata.ClassMetadata(
                                                                                                         fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleDynamicSyncPhase",
                                                                                                         properties = listOf(
                                                                                                           OwnPropertyMetadata(isComputable = false,
                                                                                                                               isKey = false,
                                                                                                                               isOpen = false,
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
                                                                                                           OwnPropertyMetadata(isComputable = false,
                                                                                                                               isKey = false,
                                                                                                                               isOpen = false,
                                                                                                                               name = "name",
                                                                                                                               valueType = primitiveTypeStringNotNullable,
                                                                                                                               withDefault = false)),
                                                                                                         supertypes = listOf("kotlin.Comparable",
                                                                                                                             "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase",
                                                                                                                             "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase\$Dynamic",
                                                                                                                             "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase.Dynamic")),
                                                                                                       FinalClassMetadata.ClassMetadata(
                                                                                                         fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleDataServicesSyncPhase",
                                                                                                         properties = listOf(
                                                                                                           OwnPropertyMetadata(isComputable = false,
                                                                                                                               isKey = false,
                                                                                                                               isOpen = false,
                                                                                                                               name = "name",
                                                                                                                               valueType = primitiveTypeStringNotNullable,
                                                                                                                               withDefault = false)),
                                                                                                         supertypes = listOf("kotlin.Comparable",
                                                                                                                             "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase",
                                                                                                                             "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase\$DataServices",
                                                                                                                             "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase.DataServices")),
                                                                                                       FinalClassMetadata.ClassMetadata(
                                                                                                         fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleStaticSyncPhase",
                                                                                                         properties = listOf(
                                                                                                           OwnPropertyMetadata(isComputable = false,
                                                                                                                               isKey = false,
                                                                                                                               isOpen = false,
                                                                                                                               name = "name",
                                                                                                                               valueType = primitiveTypeStringNotNullable,
                                                                                                                               withDefault = false),
                                                                                                           OwnPropertyMetadata(isComputable = false,
                                                                                                                               isKey = false,
                                                                                                                               isOpen = false,
                                                                                                                               name = "order",
                                                                                                                               valueType = primitiveTypeIntNotNullable,
                                                                                                                               withDefault = false)),
                                                                                                         supertypes = listOf("kotlin.Comparable",
                                                                                                                             "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase",
                                                                                                                             "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase\$Static",
                                                                                                                             "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase.Static"))),
                                                                                   supertypes = listOf("java.lang.Comparable",
                                                                                                       "kotlin.Comparable"))),
                                                                               withDefault = false),
                                                           OwnPropertyMetadata(isComputable = false,
                                                                               isKey = false,
                                                                               isOpen = false,
                                                                               name = "projectPath",
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
                                       supertypes = listOf("com.intellij.platform.workspace.storage.EntitySource",
                                                           "org.jetbrains.plugins.gradle.service.syncAction.GradleEntitySource"))

    addMetadata(typeMetadata)

    typeMetadata =
      FinalClassMetadata.ClassMetadata(fqName = "org.jetbrains.plugins.gradle.service.syncAction.impl.contributors.GradleProjectRootSyncContributor\$GradleProjectRootEntitySource",
                                       properties = listOf(OwnPropertyMetadata(isComputable = false,
                                                                               isKey = false,
                                                                               isOpen = false,
                                                                               name = "phase",
                                                                               valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                 isNullable = false,
                                                                                 typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(
                                                                                   fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase",
                                                                                   subclasses = listOf(FinalClassMetadata.ObjectMetadata(
                                                                                     fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleBaseScriptSyncPhase",
                                                                                     properties = listOf(OwnPropertyMetadata(isComputable = false,
                                                                                                                             isKey = false,
                                                                                                                             isOpen = false,
                                                                                                                             name = "name",
                                                                                                                             valueType = primitiveTypeStringNotNullable,
                                                                                                                             withDefault = false)),
                                                                                     supertypes = listOf("kotlin.Comparable",
                                                                                                         "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase",
                                                                                                         "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase\$BaseScript",
                                                                                                         "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase.BaseScript")),
                                                                                                       FinalClassMetadata.ClassMetadata(
                                                                                                         fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleDynamicSyncPhase",
                                                                                                         properties = listOf(
                                                                                                           OwnPropertyMetadata(isComputable = false,
                                                                                                                               isKey = false,
                                                                                                                               isOpen = false,
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
                                                                                                           OwnPropertyMetadata(isComputable = false,
                                                                                                                               isKey = false,
                                                                                                                               isOpen = false,
                                                                                                                               name = "name",
                                                                                                                               valueType = primitiveTypeStringNotNullable,
                                                                                                                               withDefault = false)),
                                                                                                         supertypes = listOf("kotlin.Comparable",
                                                                                                                             "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase",
                                                                                                                             "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase\$Dynamic",
                                                                                                                             "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase.Dynamic")),
                                                                                                       FinalClassMetadata.ClassMetadata(
                                                                                                         fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleDataServicesSyncPhase",
                                                                                                         properties = listOf(
                                                                                                           OwnPropertyMetadata(isComputable = false,
                                                                                                                               isKey = false,
                                                                                                                               isOpen = false,
                                                                                                                               name = "name",
                                                                                                                               valueType = primitiveTypeStringNotNullable,
                                                                                                                               withDefault = false)),
                                                                                                         supertypes = listOf("kotlin.Comparable",
                                                                                                                             "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase",
                                                                                                                             "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase\$DataServices",
                                                                                                                             "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase.DataServices")),
                                                                                                       FinalClassMetadata.ClassMetadata(
                                                                                                         fqName = "org.jetbrains.plugins.gradle.service.syncAction.GradleStaticSyncPhase",
                                                                                                         properties = listOf(
                                                                                                           OwnPropertyMetadata(isComputable = false,
                                                                                                                               isKey = false,
                                                                                                                               isOpen = false,
                                                                                                                               name = "name",
                                                                                                                               valueType = primitiveTypeStringNotNullable,
                                                                                                                               withDefault = false),
                                                                                                           OwnPropertyMetadata(isComputable = false,
                                                                                                                               isKey = false,
                                                                                                                               isOpen = false,
                                                                                                                               name = "order",
                                                                                                                               valueType = primitiveTypeIntNotNullable,
                                                                                                                               withDefault = false)),
                                                                                                         supertypes = listOf("kotlin.Comparable",
                                                                                                                             "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase",
                                                                                                                             "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase\$Static",
                                                                                                                             "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase.Static"))),
                                                                                   supertypes = listOf("java.lang.Comparable",
                                                                                                       "kotlin.Comparable"))),
                                                                               withDefault = false),
                                                           OwnPropertyMetadata(isComputable = false,
                                                                               isKey = false,
                                                                               isOpen = false,
                                                                               name = "projectPath",
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
                                       supertypes = listOf("com.intellij.platform.workspace.storage.EntitySource",
                                                           "org.jetbrains.plugins.gradle.service.syncAction.GradleEntitySource",
                                                           "org.jetbrains.plugins.gradle.service.syncAction.impl.bridge.GradleBridgeEntitySource"))

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "org.jetbrains.plugins.gradle.model.projectModel.GradleBuildEntity",
                                  entityDataFqName = "org.jetbrains.plugins.gradle.model.projectModel.impl.GradleBuildEntityData",
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
                                                                          name = "externalProject",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = false),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "externalProjectId",
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
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "name",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "url",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "projects",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "org.jetbrains.plugins.gradle.model.projectModel.GradleProjectEntity",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = false),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = true,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "symbolicId",
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
                                                                          withDefault = false)),
                                  extProperties = listOf(ExtPropertyMetadata(isComputable = false,
                                                                             isOpen = false,
                                                                             name = "gradleBuilds",
                                                                             receiverFqn = "com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntity",
                                                                             valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                           entityFqName = "org.jetbrains.plugins.gradle.model.projectModel.GradleBuildEntity",
                                                                                                                           isChild = true,
                                                                                                                           isNullable = false),
                                                                             withDefault = false)),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "org.jetbrains.plugins.gradle.model.projectModel.GradleModuleEntity",
                                  entityDataFqName = "org.jetbrains.plugins.gradle.model.projectModel.impl.GradleModuleEntityData",
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
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.jps.entities.ModuleEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = false),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "gradleProjectId",
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
                                                                                                                                    name = "identityPath",
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
                                                                             name = "gradleModuleEntity",
                                                                             receiverFqn = "com.intellij.platform.workspace.jps.entities.ModuleEntity",
                                                                             valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                                                           entityFqName = "org.jetbrains.plugins.gradle.model.projectModel.GradleModuleEntity",
                                                                                                                           isChild = true,
                                                                                                                           isNullable = true),
                                                                             withDefault = false)),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "org.jetbrains.plugins.gradle.model.projectModel.GradleProjectEntity",
                                  entityDataFqName = "org.jetbrains.plugins.gradle.model.projectModel.impl.GradleProjectEntityData",
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
                                                                          name = "build",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "org.jetbrains.plugins.gradle.model.projectModel.GradleBuildEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = false),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "buildId",
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
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "name",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "path",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "identityPath",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "url",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "linkedProjectId",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = true,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "symbolicId",
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
                                                                                                                                    name = "identityPath",
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

    typeMetadata = EntityMetadata(fqName = "org.jetbrains.plugins.gradle.model.versionCatalogs.GradleVersionCatalogEntity",
                                  entityDataFqName = "org.jetbrains.plugins.gradle.model.versionCatalogs.impl.GradleVersionCatalogEntityData",
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
                                                                          name = "url",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "build",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "org.jetbrains.plugins.gradle.model.projectModel.GradleBuildEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = false),
                                                                          withDefault = false)),
                                  extProperties = listOf(ExtPropertyMetadata(isComputable = false,
                                                                             isOpen = false,
                                                                             name = "versionCatalogs",
                                                                             receiverFqn = "org.jetbrains.plugins.gradle.model.projectModel.GradleBuildEntity",
                                                                             valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                           entityFqName = "org.jetbrains.plugins.gradle.model.versionCatalogs.GradleVersionCatalogEntity",
                                                                                                                           isChild = true,
                                                                                                                           isNullable = false),
                                                                             withDefault = false)),
                                  isAbstract = false)

    addMetadata(typeMetadata)
  }

  override fun initializeMetadataHash() {
    addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.model.projectModel.GradleBuildEntity", metadataHash = 1853164449)
    addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.model.projectModel.GradleModuleEntity", metadataHash = 418255750)
    addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.model.projectModel.GradleProjectEntity", metadataHash = 650134225)
    addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.model.versionCatalogs.GradleVersionCatalogEntity", metadataHash = -1616609179)
    addMetadataHash(typeFqn = "com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntityId",
                    metadataHash = -1650256201)
    addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.model.projectModel.GradleBuildEntityId", metadataHash = 843983143)
    addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.model.projectModel.GradleProjectEntityId", metadataHash = -443877510)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.SymbolicEntityId", metadataHash = -1894859879)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.EntitySource", metadataHash = 1674457967)
    addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.service.syncAction.GradleEntitySource", metadataHash = -565169080)
    addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.service.syncAction.impl.bridge.GradleBridgeEntitySource",
                    metadataHash = 1645182578)
    addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.service.syncAction.impl.bridge.GradleBridgeFinalizerDataService\$DataServiceEntitySource",
                    metadataHash = -1359272613)
    addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase", metadataHash = -754421906)
    addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase\$BaseScript", metadataHash = -142884038)
    addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.service.syncAction.GradleBaseScriptSyncPhase", metadataHash = -1450037938)
    addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase\$DataServices", metadataHash = -1256475695)
    addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.service.syncAction.GradleDataServicesSyncPhase", metadataHash = -1556399787)
    addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase\$Dynamic", metadataHash = -784456624)
    addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.service.syncAction.GradleDynamicSyncPhase", metadataHash = -2088811711)
    addMetadataHash(typeFqn = "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase", metadataHash = -407549003)
    addMetadataHash(typeFqn = "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase\$BuildFinished",
                    metadataHash = 64803236)
    addMetadataHash(typeFqn = "com.intellij.gradle.toolingExtension.modelAction.GradleBuildFinishedModelFetchPhase",
                    metadataHash = -651389069)
    addMetadataHash(typeFqn = "com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase\$ProjectLoaded",
                    metadataHash = 1151381984)
    addMetadataHash(typeFqn = "com.intellij.gradle.toolingExtension.modelAction.GradleProjectLoadedModelFetchPhase",
                    metadataHash = -1086434639)
    addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase\$Static", metadataHash = -1839677424)
    addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.service.syncAction.GradleStaticSyncPhase", metadataHash = -181947250)
    addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.service.syncAction.impl.bridge.GradleBridgeModuleDataService\$GradleBridgeModuleEntitySource",
                    metadataHash = -118266230)
    addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.service.syncAction.impl.contributors.GradleContentRootSyncContributor\$GradleContentRootEntitySource",
                    metadataHash = -754914382)
    addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.service.syncAction.impl.contributors.GradleContentRootSyncContributor\$GradleProjectModelEntitySource",
                    metadataHash = 421132051)
    addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.service.syncAction.impl.contributors.GradleProjectRootSyncContributor\$GradleProjectRootEntitySource",
                    metadataHash = 2014769394)
    addMetadataHash(typeFqn = "org.jetbrains.plugins.gradle.service.syncAction.impl.contributors.GradleVersionCatalogSyncContributor\$GradleVersionCatalogEntitySource",
                    metadataHash = 1457290656)
  }
}
